package pro.check;

import java.util.Calendar;

import pro.charge.Charge;
import pro.charge.Charge.ErrorCode;
import pro.charge.Charge.Reason;
import pro.define.ChargeThreadObject;
import pro.define.ChargeThreadObject.ThreadStatus;
import pro.server.Common;
import pro.server.Program;
import pro.server.LocalConfig;
import uti.utility.MyCheck;
import uti.utility.MyConfig;
import uti.utility.MyConfig.ChannelType;
import uti.utility.MyConvert;
import uti.utility.MyDate;
import uti.utility.MyLogger;
import dat.service.ChargeLog;
import dat.service.DefineMT.MTType;
import dat.service.MOLog;
import dat.sub.Subscriber;
import dat.sub.Subscriber.Status;
import dat.sub.UnSubscriber;
import db.define.MyDataRow;
import db.define.MyTableModel;

/**
 * Thread sẽ bắn tin cho từng dịch vụ
 * 
 * @author Administrator
 * 
 */
public class ChargeRenew extends Thread
{
	MyLogger mLog = new MyLogger(LocalConfig.LogConfigPath,this.getClass().toString());

	public ChargeThreadObject mCTObject = new ChargeThreadObject();

	public ChargeRenew()
	{

	}

	public ChargeRenew(ChargeThreadObject mCTObject)
	{
		this.mCTObject = mCTObject;
	}

	Subscriber mSub = null;
	UnSubscriber mUnSub = null;
	MOLog mMOLog = null;
	ChargeLog mChargeLog = null;

	MyTableModel mTable_SubUpdate = null;
	MyTableModel mTable_ChargeLog = null;
	MyTableModel mTable_MOLog = null;
	pro.charge.Charge mCharge = new Charge();

	int TotalCount = 0;

	// public SimpleDateFormat MyConfig.Get_DateFormat_InsertDB() = new
	// SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	public void run()
	{
		if (Program.processData)
		{
			try
			{
				mCharge.AllowInsertChargeLog = false;

				mSub = new Subscriber(LocalConfig.mDBConfig_MSSQL);
				mUnSub = new UnSubscriber(LocalConfig.mDBConfig_MSSQL);
				mTable_SubUpdate = mSub.Select(0);
				mTable_SubUpdate.Clear();

				mChargeLog = new ChargeLog(LocalConfig.mDBConfig_MSSQL);
				mTable_ChargeLog = mChargeLog.Select(0);

				mMOLog = new MOLog(LocalConfig.mDBConfig_MSSQL);
				mTable_MOLog = mMOLog.Select(0);

				PushForEach();
			}
			catch (Exception ex)
			{
				mCTObject.mThreadStatus = ThreadStatus.Error;

				mLog.log.error("Loi xay ra trong qua trinh Charging, Thead Index:" + mCTObject.ProcessIndex, ex);
			}
		}
	}

	private boolean PushForEach() throws Exception
	{
		MyTableModel mTable = new MyTableModel(null, null);
		try
		{
			Integer MinPID = 0;

			if (mCTObject.CurrentPID > 0) MinPID = mCTObject.CurrentPID;

			for (Integer PID = MinPID; PID <= LocalConfig.MAX_PID; PID++)
			{
				mCTObject.CurrentPID = PID;
				mCTObject.MaxOrderID = 0;

				mTable = GetSubscriber(PID);

				while (!mTable.IsEmpty())
				{
					for (Integer i = 0; i < mTable.GetRowCount(); i++)
					{
						// nếu bị dừng đột ngột
						if (!Program.processData)
						{
							mLog.log.debug("Bi dung Charge: Charge Info:" + mCTObject.GetLogString(""));

							mCTObject.mThreadStatus = ThreadStatus.Stop;
							mCTObject.QueueDate = Calendar.getInstance().getTime();

							Insert_ChargeLog();
							UpdateCharge();
							return false;
						}

						TotalCount++;

						mCTObject.MaxOrderID = Integer.parseInt(mTable.GetValueAt(i, "OrderID").toString());

						String MSISDN = mTable.GetValueAt(i, "MSISDN").toString();
						Integer PartnerID = 0;
						if (mTable.GetValueAt(i, "PartnerID") != null)
						{
							PartnerID = Integer.parseInt(mTable.GetValueAt(i, "PartnerID").toString());
						}
						if (!MSISDN.startsWith("84")) MSISDN = MyCheck.ValidPhoneNumber(MSISDN, "84");

						mCTObject.MSISDN = MSISDN;

						Calendar mCal_Current = Calendar.getInstance();
						Calendar mCal_ExpireDate = Calendar.getInstance();

						mCal_ExpireDate.setTime(MyConfig.Get_DateFormat_InsertDB().parse(
								mTable.GetValueAt(i, "ExpiryDate").toString()));

						Long CountDay = MyDate.diffDays(mCal_ExpireDate, mCal_Current);

						if (CountDay < 1)
						{
							// nếu chưa hết hạn thì không tiến hành xử
							// lý charge
							continue;
						}

						Subscriber.Status mStatus = Status.FromInt(Integer.parseInt(mTable.GetValueAt(i, "StatusID")
								.toString()));
						Integer RetryChargeCount = 0;

						if (mTable.GetValueAt(i, "RetryChargeCount") != null)
							RetryChargeCount = Integer.parseInt(mTable.GetValueAt(i, "RetryChargeCount").toString());

						MyConfig.ChannelType mChannel = MyConfig.ChannelType.FromInt(Integer.parseInt(mTable
								.GetValueAt(i, "ChannelTypeID").toString()));

						Integer RenewMark = LocalConfig.RetryMark_5000;

						ErrorCode mResultCharge = mCharge.ChargeRenew(PartnerID, mCTObject.MSISDN, 5000,
								mCTObject.Keyword, mChannel);

						AddToChargeLog(PartnerID, MSISDN, 5000, pro.charge.Charge.Reason.RENEW_DAILY, 5000, 0, mChannel,
								mResultCharge);

						if (mResultCharge == ErrorCode.BlanceTooLow)
						{
							RenewMark = LocalConfig.RetryMark_3000;
							mResultCharge = mCharge.ChargeRenew(PartnerID, mCTObject.MSISDN, 3000, mCTObject.Keyword,
									mChannel);
							AddToChargeLog(PartnerID, MSISDN, 3000, pro.charge.Charge.Reason.RENEW_DAILY, 3000, 0,
									mChannel, mResultCharge);
						}
						if (mResultCharge == ErrorCode.BlanceTooLow)
						{
							RenewMark = LocalConfig.RetryMark_1000;
							mResultCharge = mCharge.ChargeRenew(PartnerID, mCTObject.MSISDN, 1000, mCTObject.Keyword,
									mChannel);
							AddToChargeLog(PartnerID, MSISDN, 1000, pro.charge.Charge.Reason.RENEW_DAILY, 1000, 0,
									mChannel, mResultCharge);
						}

						// Theo yêu cầu của VNP thì các trường hợp này phải hủy,
						// chi tiết hãy đọc tài liêu
						if (mResultCharge == ErrorCode.SubDoesNotExist)
						{
							mTable.SetValueAt(MyConfig.Get_DateFormat_InsertDB().format(mCal_Current.getTime()), i,
									"RetryChargeDate");

							// Hủy nhưng ko gửi MT
							DeregSub(mTable.GetRow(i), false);
							continue;
						}

						if (mResultCharge != ErrorCode.ChargeSuccess && mStatus == Status.ChargeFail
								&& CountDay >= LocalConfig.CHARGE_MAX_DAY_RETRY && mCTObject.AllowDereg)
						{
							mTable.SetValueAt(MyConfig.Get_DateFormat_InsertDB().format(mCal_Current.getTime()), i,
									"RetryChargeDate");

							DeregSub(mTable.GetRow(i), true);
							continue;
						}

						if (mResultCharge == ErrorCode.ChargeSuccess)
						{
							Integer ChargeMark = Integer.parseInt(mTable.GetValueAt(i, "MOByDay").toString());

							Integer CodeByDay = Integer.parseInt(mTable.GetValueAt(i, "CodeByDay").toString());
							Integer TotalCode = Integer.parseInt(mTable.GetValueAt(i, "TotalCode").toString());

							ChargeMark = RenewMark;

							CodeByDay = RenewMark / LocalConfig.MarkPerCode;
							TotalCode += RenewMark / LocalConfig.MarkPerCode;

							mTable.SetValueAt(ChargeMark, i, "ChargeMark");

							mTable.SetValueAt(CodeByDay, i, "CodeByDay");
							mTable.SetValueAt(TotalCode, i, "TotalCode");

							mCal_ExpireDate.set(Calendar.MILLISECOND, 0);
							mCal_ExpireDate.set(mCal_Current.get(Calendar.YEAR), mCal_Current.get(Calendar.MONTH),
									mCal_Current.get(Calendar.DATE), 23, 59, 59);

							mTable.SetValueAt(MyConfig.Get_DateFormat_InsertDB().format(mCal_Current.getTime()), i,
									"ChargeDate");

							mTable.SetValueAt(MyConfig.Get_DateFormat_InsertDB().format(mCal_Current.getTime()), i,
									"RenewChargeDate");

							mTable.SetValueAt(MyConfig.Get_DateFormat_InsertDB().format(mCal_ExpireDate.getTime()), i,
									"ExpiryDate");
							mTable.SetValueAt(0, i, "RetryChargeCount");
							mTable.SetValueAt(Status.Active.GetValue(), i, "StatusID");

							// Tăng số MT bắn thành công
							mCTObject.SuccessNumber++;
						}
						else
						{
							mTable.SetValueAt(MyConfig.Get_DateFormat_InsertDB().format(mCal_Current.getTime()), i,
									"RetryChargeDate");
							mTable.SetValueAt(RetryChargeCount + 1, i, "RetryChargeCount");
							mTable.SetValueAt(Status.ChargeFail.GetValue(), i, "StatusID");

							// Tăng số MT bắn không thành công
							mCTObject.FailNumber++;

							// Ghi lại các trường hợp chưa bắn được MT
							// để sau này push lại
							mCTObject.QueueDate = Calendar.getInstance().getTime();
						}

						MyDataRow mUpdateRow = mTable.GetRow(i).clone();
						mTable_SubUpdate.AddNewRow(mUpdateRow);

					}
					mLog.log.debug("Tien Hanh charge cho:" + TotalCount + " thue bao ProcessIndex:"
							+ mCTObject.ProcessIndex);
					Insert_ChargeLog();
					UpdateCharge();

					mTable.Clear();
					mTable = GetSubscriber(PID);
				}
			}
			mCTObject.mThreadStatus = ThreadStatus.Complete;
			return true;
		}
		catch (Exception ex)
		{
			mLog.log.error("Loi trong charge renew cho dich vu", ex);
			throw ex;
		}
		finally
		{
			Insert_ChargeLog();
			UpdateCharge();
			// Cập nhật thời gian kết thúc bắn tin
			mCTObject.FinishDate = Calendar.getInstance().getTime();

			mLog.log.debug("KET THUC CHARGING ProcessIndex:" + mCTObject.ProcessIndex + "|PID:" + mCTObject.CurrentPID
					+ "|OrderID:" + mCTObject.MaxOrderID + "|TotalCount:" + TotalCount);
		}
	}

	private MyTableModel AddInfo(MyDataRow mRow) throws Exception
	{
		try
		{
			MyTableModel mTable_UnSub = mUnSub.Select(0);
			mTable_UnSub.Clear();

			// Tạo row để insert vào Table Sub
			MyDataRow mRow_Sub = mTable_UnSub.CreateNewRow();
			mRow_Sub.SetValueCell("MSISDN", mRow.GetValueCell("MSISDN"));

			mRow_Sub.SetValueCell("FirstDate", mRow.GetValueCell("FirstDate"));
			mRow_Sub.SetValueCell("EffectiveDate", mRow.GetValueCell("EffectiveDate"));
			mRow_Sub.SetValueCell("ExpiryDate", mRow.GetValueCell("ExpiryDate"));

			mRow_Sub.SetValueCell("RetryChargeCount", mRow.GetValueCell("RetryChargeCount"));

			if (mRow.GetValueCell("RetryChargeDate") != null)
				mRow_Sub.SetValueCell("RetryChargeDate", mRow.GetValueCell("RetryChargeDate"));

			if (mRow.GetValueCell("ChargeDate") != null)
				mRow_Sub.SetValueCell("ChargeDate", mRow.GetValueCell("ChargeDate"));

			if (mRow.GetValueCell("RenewChargeDate") != null)
				mRow_Sub.SetValueCell("RenewChargeDate", mRow.GetValueCell("RenewChargeDate"));

			mRow_Sub.SetValueCell("ChannelTypeID", mRow.GetValueCell("ChannelTypeID"));
			mRow_Sub.SetValueCell("StatusID", mRow.GetValueCell("StatusID"));
			mRow_Sub.SetValueCell("PID", mRow.GetValueCell("PID"));
			mRow_Sub.SetValueCell("OrderID", mRow.GetValueCell("OrderID"));

			mRow_Sub.SetValueCell("MOByDay", mRow.GetValueCell("MOByDay"));
			mRow_Sub.SetValueCell("ChargeMark", mRow.GetValueCell("ChargeMark"));
			mRow_Sub.SetValueCell("WeekMark", mRow.GetValueCell("WeekMark"));
			mRow_Sub.SetValueCell("CodeByDay", mRow.GetValueCell("CodeByDay"));
			mRow_Sub.SetValueCell("TotalCode", mRow.GetValueCell("TotalCode"));
			mRow_Sub.SetValueCell("MatchID", mRow.GetValueCell("MatchID"));
			mRow_Sub.SetValueCell("IsNotify", mRow.GetValueCell("IsNotify"));

			if (mRow.GetValueCell("LastUpdate") != null)
				mRow_Sub.SetValueCell("LastUpdate", mRow.GetValueCell("LastUpdate"));

			if (mRow.GetValueCell("CofirmDeregDate") != null)
				mRow_Sub.SetValueCell("CofirmDeregDate", mRow.GetValueCell("CofirmDeregDate"));

			if (mRow.GetValueCell("PartnerID") != null)
				mRow_Sub.SetValueCell("PartnerID", mRow.GetValueCell("PartnerID"));
			
			mRow_Sub.SetValueCell("DeregDate",
					MyConfig.Get_DateFormat_InsertDB().format(Calendar.getInstance().getTime()));

			mTable_UnSub.AddNewRow(mRow_Sub);
			return mTable_UnSub;
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

	private void AddToChargeLog(Integer PartnerID, String MSISDN, Integer PRICE, Reason REASON, Integer ORIGINALPRICE,
			Integer PROMOTION, MyConfig.ChannelType CHANNEL, ErrorCode mResult)
	{
		try
		{
			Calendar CurrentDate = Calendar.getInstance();

			MyDataRow mRow_Log = mTable_ChargeLog.CreateNewRow();

			mRow_Log.SetValueCell("MSISDN", MSISDN);
			mRow_Log.SetValueCell("ChargeDate", MyConfig.Get_DateFormat_InsertDB().format(CurrentDate.getTime()));
			mRow_Log.SetValueCell("ChargeTypeID", REASON.GetValue());
			mRow_Log.SetValueCell("ChargeTypeName", REASON.toString());
			mRow_Log.SetValueCell("ChargeStatusID", mResult.GetValue());
			mRow_Log.SetValueCell("ChargeStatusName", mResult.toString());
			mRow_Log.SetValueCell("IsPromotion", PROMOTION.toString());
			mRow_Log.SetValueCell("ChannelTypeID", CHANNEL.GetValue());
			mRow_Log.SetValueCell("ChannelTypeName", CHANNEL.toString());
			mRow_Log.SetValueCell("Price", PRICE);
			mRow_Log.SetValueCell("PID", MyConvert.GetPIDByMSISDN(MSISDN, LocalConfig.MAX_PID));
			mRow_Log.SetValueCell("PartnerID", PartnerID);
			mTable_ChargeLog.AddNewRow(mRow_Log);

		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

	private void Insert_ChargeLog() throws Exception
	{
		try
		{
			if (mTable_ChargeLog.GetRowCount() < 1) return;

			mChargeLog.Insert(0, mTable_ChargeLog.GetXML());
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
		finally
		{
			mTable_ChargeLog.Clear();
		}

	}

	private void Insert_MOLog(MyDataRow mRow, MTType mMTType, ChannelType mChannel, String MTContent) throws Exception
	{
		try
		{
			mTable_MOLog.Clear();
			MyDataRow mRow_Log = mTable_MOLog.CreateNewRow();

			mRow_Log.SetValueCell("MSISDN", mRow.GetValueCell("MSISDN"));

			mRow_Log.SetValueCell("LogDate", MyConfig.Get_DateFormat_InsertDB()
					.format(Calendar.getInstance().getTime()));
			mRow_Log.SetValueCell("ChannelTypeID", mChannel.GetValue());
			mRow_Log.SetValueCell("ChannelTypeName", mChannel.toString());
			mRow_Log.SetValueCell("MTTypeID", mMTType.GetValue());
			mRow_Log.SetValueCell("MTTypeName", mMTType.toString());
			mRow_Log.SetValueCell("MO", "");
			mRow_Log.SetValueCell("MT", MTContent);
			mRow_Log.SetValueCell("LogContent", "Renew Service Fail");
			mRow_Log.SetValueCell("PID", mRow.GetValueCell("PID"));
			mRow_Log.SetValueCell("RequestID", "0");

			mTable_MOLog.AddNewRow(mRow_Log);

			mMOLog.Insert(0, mTable_MOLog.GetXML());
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
		finally
		{
			mTable_MOLog.Clear();
		}
	}

	/**
	 * Hủy dịch vụ một số thuê bao khi charge không thành công
	 */
	private void DeregSub(MyDataRow mRow, boolean AllowSendMT)
	{
		try
		{
			MyTableModel mTable = AddInfo(mRow.clone());

			Integer PartnerID = 0;
			if (mRow.GetValueCell("PartnerID") != null)
			{
				PartnerID = Integer.parseInt(mRow.GetValueCell("PartnerID").toString());
			}

			String XML = mTable.GetXML();

			// Tiến hành hủy đăng ký khi mà retry không
			// thành công
			if (ErrorCode.ChargeSuccess != mCharge.ChargeDereg(PartnerID, mCTObject.MSISDN, mCTObject.Keyword,
					MyConfig.ChannelType.MAXRETRY))
			{
				MyLogger.WriteDataLog(LocalConfig.LogDataFolder, "_Charge_Sync_Dereg_VNP_FAIL",
						"DEREG RECORD FAIL --> " + XML);
			}

			if (mUnSub.Move(0, XML))
			{
				// Có những trường hợp Hủy nhưng ko cần gửi MT
				if (AllowSendMT)
				{
					String MTContent = Common.GetDefineMT_Message(MTType.ExtendDereg);

					if (Common.SendMT(mCTObject, MTContent))
						Insert_MOLog(mRow, MTType.ExtendDereg, MyConfig.ChannelType.MAXRETRY, MTContent);
				}
				else
				{
					MyLogger.WriteDataLog(LocalConfig.LogDataFolder, "_Charge_Sync_Dereg_NOT_SEND_MT", "INFO --> "
							+ XML);
				}
			}
			else
			{
				MyLogger.WriteDataLog(LocalConfig.LogDataFolder, "_Charge_NotMoveToUnSub", "DEREG RECORD FAIL --> "
						+ XML);
			}
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

	private void UpdateCharge() throws Exception
	{
		String XML = "";
		try
		{
			if (mTable_SubUpdate.IsEmpty()) return;

			XML = mTable_SubUpdate.GetXML();

			if (!mSub.UpdateCharge(0, XML))
			{
				MyLogger.WriteDataLog(LocalConfig.LogDataFolder, "_Charge_NotUpdateDB", "LIST RECORD --> " + XML);
			}
		}
		catch (Exception ex)
		{
			MyLogger.WriteDataLog(LocalConfig.LogDataFolder, "_Charge_NotUpdateDB", "LIST RECORD --> " + XML);
			mLog.log.error(ex);
		}
		finally
		{
			mTable_SubUpdate.Clear();
		}
	}

	/**
	 * Lấy dữ liệu từ database
	 * 
	 * @return
	 * @throws Exception
	 */
	public MyTableModel GetSubscriber(Integer PID) throws Exception
	{
		try
		{
			// Lấy danh sách(Para_1 = RowCount, Para_2 = PID, Para_3 = OrderID,
			// Para_4 = ProcessNumber, Para_5 = ProcessIndex )
			return mSub.Select(5, mCTObject.RowCount.toString(), PID.toString(), mCTObject.MaxOrderID.toString(),
					mCTObject.ProcessNumber.toString(), mCTObject.ProcessIndex.toString());
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

}
