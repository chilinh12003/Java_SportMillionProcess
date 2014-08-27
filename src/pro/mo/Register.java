package pro.mo;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Vector;

import pro.charge.Charge;
import pro.charge.Charge.ErrorCode;
import pro.server.Common;
import pro.server.ContentAbstract;
import pro.server.Keyword;
import pro.server.LocalConfig;
import pro.server.MsgObject;
import uti.utility.MyConfig;
import uti.utility.MyConvert;
import uti.utility.MyLogger;
import dat.service.DefineMT;
import dat.service.DefineMT.MTType;
import dat.service.MOLog;
import dat.service.MatchObject;
import dat.service.News.NewsType;
import dat.service.NewsObject;
import dat.sub.Subscriber;
import dat.sub.SubscriberObject;
import dat.sub.UnSubscriber;
import db.define.MyDataRow;
import db.define.MyTableModel;

public class Register extends ContentAbstract
{
	MyLogger mLog = new MyLogger(LocalConfig.LogConfigPath,this.getClass().toString());
	Collection<MsgObject> ListMessOject = new ArrayList<MsgObject>();

	MsgObject mMsgObject = null;
	SubscriberObject mSubObj = new SubscriberObject();

	MatchObject mMatchObj = new MatchObject();

	Calendar mCal_Current = Calendar.getInstance();
	Calendar mCal_SendMO = Calendar.getInstance();
	Calendar mCal_Expire = Calendar.getInstance();

	Subscriber mSub = null;
	UnSubscriber mUnSub = null;
	MOLog mMOLog = null;
	dat.service.Keyword mKeyword = null;

	MyTableModel mTable_MOLog = null;
	MyTableModel mTable_Sub = null;

	pro.charge.Charge mCharge = new Charge();
	DefineMT.MTType mMTType = MTType.RegFail;

	String MTContent = "";

	/**
	 * ID của đối tác, khi đăng ký qua các kênh của đối tác
	 */
	Integer PartnerID = 0;

	// Thời gian miễn phí để chèn vào MT trả về cho khách hàng
	String FreeTime = "ngay dau tien";

	private void Init(MsgObject msgObject, Keyword keyword) throws Exception
	{
		try
		{
			mSub = new Subscriber(LocalConfig.mDBConfig_MSSQL);
			mUnSub = new UnSubscriber(LocalConfig.mDBConfig_MSSQL);
			mMOLog = new MOLog(LocalConfig.mDBConfig_MSSQL);
			mKeyword = new dat.service.Keyword(LocalConfig.mDBConfig_MSSQL);

			mTable_MOLog = mMOLog.Select(0);
			mTable_Sub = mSub.Select(0);

			mMsgObject = msgObject;

			mCal_SendMO.setTime(mMsgObject.getTTimes());

			mCal_Expire.set(Calendar.MILLISECOND, 0);
			mCal_Expire.set(mCal_Current.get(Calendar.YEAR), mCal_Current.get(Calendar.MONTH),
					mCal_Current.get(Calendar.DATE), 23, 59, 59);

		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

	private Collection<MsgObject> AddToList() throws Exception
	{
		try
		{
			ListMessOject.clear();
			MTContent = Common.GetDefineMT_Message(mSubObj, mMatchObj, mMTType);
			MTContent = MTContent.replace("[FreeTime]", FreeTime);

			if (!MTContent.equalsIgnoreCase(""))
			{
				mMsgObject.setUsertext(MTContent);
				mMsgObject.setContenttype(LocalConfig.LONG_MESSAGE_CONTENT_TYPE);
				mMsgObject.setMsgtype(1);
				ListMessOject.add(new MsgObject(mMsgObject.clone()));
				AddToMOLog(mMTType, MTContent);
			}

			if (mMTType == MTType.RegNewSuccess || mMTType == MTType.RegAgainSuccessFree
					|| mMTType == MTType.RegAgainSuccessNotFree)
			{
				Calendar mCal_Begin = Calendar.getInstance();
				Calendar mCal_End = Calendar.getInstance();

				mCal_Begin.set(Calendar.MINUTE, 0);
				mCal_Begin.set(Calendar.SECOND, 0);

				mCal_End.set(Calendar.MINUTE, 59);
				mCal_End.set(Calendar.SECOND, 59);

				Vector<NewsObject> mListNews = Common.Get_List_Two_News();

				String MTContent_Current_Push = "";
				String MTContent_Current_Remider = "";
				if (mListNews.size() > 0)
				{
					for (NewsObject mNewObj : mListNews)
					{
						if (mNewObj.mNewsType == NewsType.Push) MTContent_Current_Push = mNewObj.Content;

						if (mNewObj.mNewsType == NewsType.Reminder) MTContent_Current_Remider = mNewObj.Content;

					}
				}

				if (!MTContent_Current_Push.equalsIgnoreCase(""))
				{
					// Đăng ký lại (hủy xong rồi đăng ký)
					mMsgObject.setUsertext(MTContent_Current_Push);
					mMsgObject.setContenttype(LocalConfig.LONG_MESSAGE_CONTENT_TYPE);
					mMsgObject.setMsgtype(1);
					ListMessOject.add(new MsgObject(mMsgObject.clone()));
					AddToMOLog(MTType.PushMT, MTContent_Current_Push);
				}

				if (!MTContent_Current_Remider.equalsIgnoreCase(""))
				{
					mMsgObject.setUsertext(MTContent_Current_Remider);
					mMsgObject.setContenttype(LocalConfig.LONG_MESSAGE_CONTENT_TYPE);
					mMsgObject.setMsgtype(1);
					ListMessOject.add(new MsgObject(mMsgObject.clone()));
					AddToMOLog(MTType.PushMTReminder, MTContent_Current_Remider);
				}
			}

			return ListMessOject;
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

	private void AddToMOLog(MTType mMTType_Current, String MTContent_Current) throws Exception
	{
		try
		{
			MyDataRow mRow_Log = mTable_MOLog.CreateNewRow();

			mRow_Log.SetValueCell("MatchID", mMatchObj.MatchID);
			mRow_Log.SetValueCell("MSISDN", mMsgObject.getUserid());
			mRow_Log.SetValueCell("LogDate", MyConfig.Get_DateFormat_InsertDB().format(mCal_Current.getTime()));
			mRow_Log.SetValueCell("ChannelTypeID", mMsgObject.getChannelType());
			mRow_Log.SetValueCell("ChannelTypeName", MyConfig.ChannelType.FromInt(mMsgObject.getChannelType())
					.toString());
			mRow_Log.SetValueCell("MTTypeID", mMTType_Current.GetValue());
			mRow_Log.SetValueCell("MTTypeName", mMTType_Current.toString());
			mRow_Log.SetValueCell("MO", mMsgObject.getMO());
			mRow_Log.SetValueCell("MT", MTContent_Current);
			mRow_Log.SetValueCell("PID", MyConvert.GetPIDByMSISDN(mMsgObject.getUserid(), LocalConfig.MAX_PID));
			mRow_Log.SetValueCell("RequestID", mMsgObject.getRequestid().toString());
			mRow_Log.SetValueCell("PartnerID", PartnerID);
			mTable_MOLog.AddNewRow(mRow_Log);

		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

	private void Insert_MOLog() throws Exception
	{
		try
		{
			MOLog mMOLog = new MOLog(LocalConfig.mDBConfig_MSSQL);
			mMOLog.Insert(0, mTable_MOLog.GetXML());
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

	private MyTableModel AddInfo() throws Exception
	{
		try
		{
			mTable_Sub.Clear();

			// Tạo row để insert vào Table Sub
			MyDataRow mRow_Sub = mTable_Sub.CreateNewRow();
			mRow_Sub.SetValueCell("MSISDN", mSubObj.MSISDN);

			mRow_Sub.SetValueCell("FirstDate", MyConfig.Get_DateFormat_InsertDB().format(mSubObj.FirstDate));
			mRow_Sub.SetValueCell("EffectiveDate", MyConfig.Get_DateFormat_InsertDB().format(mSubObj.EffectiveDate));
			mRow_Sub.SetValueCell("ExpiryDate", MyConfig.Get_DateFormat_InsertDB().format(mSubObj.ExpiryDate));

			mRow_Sub.SetValueCell("RetryChargeCount", mSubObj.RetryChargeCount);

			if (mSubObj.ChargeDate != null)
				mRow_Sub.SetValueCell("ChargeDate", MyConfig.Get_DateFormat_InsertDB().format(mSubObj.ChargeDate));

			if (mSubObj.RenewChargeDate != null)
				mRow_Sub.SetValueCell("RenewChargeDate",
						MyConfig.Get_DateFormat_InsertDB().format(mSubObj.RenewChargeDate));

			if (mSubObj.RetryChargeDate != null)
				mRow_Sub.SetValueCell("RetryChargeDate",
						MyConfig.Get_DateFormat_InsertDB().format(mSubObj.RetryChargeDate));

			mRow_Sub.SetValueCell("ChannelTypeID", mSubObj.ChannelTypeID);
			mRow_Sub.SetValueCell("StatusID", mSubObj.StatusID);
			mRow_Sub.SetValueCell("PID", mSubObj.PID);

			mRow_Sub.SetValueCell("MOByDay", mSubObj.MOByDay);
			mRow_Sub.SetValueCell("ChargeMark", mSubObj.ChargeMark);
			mRow_Sub.SetValueCell("WeekMark", mSubObj.WeekMark);
			mRow_Sub.SetValueCell("CodeByDay", mSubObj.CodeByDay);
			mRow_Sub.SetValueCell("TotalCode", mSubObj.TotalCode);
			mRow_Sub.SetValueCell("MatchID", mMatchObj.MatchID);
			mRow_Sub.SetValueCell("IsNotify", mSubObj.IsNotify);

			mRow_Sub.SetValueCell("AnswerKQ", mSubObj.AnswerKQ);
			mRow_Sub.SetValueCell("AnswerBT", mSubObj.AnswerBT);
			mRow_Sub.SetValueCell("AnswerGB", mSubObj.AnswerGB);
			mRow_Sub.SetValueCell("AnswerTS", mSubObj.AnswerTS);
			mRow_Sub.SetValueCell("AnswerTV", mSubObj.AnswerTV);
			mRow_Sub.SetValueCell("PartnerID", PartnerID);

			if (mSubObj.LastUpdate != null)
				mRow_Sub.SetValueCell("LastUpdate", MyConfig.Get_DateFormat_InsertDB().format(mSubObj.LastUpdate));

			if (mSubObj.DeregDate != null)
				mRow_Sub.SetValueCell("DeregDate", MyConfig.Get_DateFormat_InsertDB().format(mSubObj.DeregDate));

			if (mSubObj.CofirmDeregDate != null)
				mRow_Sub.SetValueCell("CofirmDeregDate",
						MyConfig.Get_DateFormat_InsertDB().format(mSubObj.CofirmDeregDate));

			mTable_Sub.AddNewRow(mRow_Sub);
			return mTable_Sub;
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

	private boolean Insert_Sub() throws Exception
	{
		try
		{
			MyTableModel mTable_Sub = AddInfo();

			if (!mSub.Insert(0, mTable_Sub.GetXML()))
			{
				mLog.log.info("Insert vao table Subscriber KHONG THANH CONG: XML Insert-->" + mTable_Sub.GetXML());
				return false;
			}

			return true;
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

	private boolean MoveToUnSub() throws Exception
	{
		try
		{
			MyTableModel mTable_Sub = AddInfo();

			if (!mSub.Move(0, mTable_Sub.GetXML()))
			{
				mLog.log.info("Move tu UnSub Sang Sub KHONG THANH CONG: XML Insert-->" + mTable_Sub.GetXML());
				return false;
			}

			return true;
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

	/**
	 * tạo dữ liệu cho những đăng ký lại (trước đó đã hủy dịch vụ)
	 * 
	 * @throws Exception
	 */
	private void CreateRegAgain() throws Exception
	{
		try
		{
			mSubObj.ChannelTypeID = mMsgObject.getChannelType();

			mSubObj.EffectiveDate = mCal_Current.getTime();
			mSubObj.ExpiryDate = mCal_Expire.getTime();

			mSubObj.LastUpdate = mCal_Current.getTime();
			mSubObj.MSISDN = mMsgObject.getUserid();
			mSubObj.PID = MyConvert.GetPIDByMSISDN(mSubObj.MSISDN, LocalConfig.MAX_PID);
			mSubObj.StatusID = dat.sub.Subscriber.Status.Active.GetValue();

			mSubObj.ChargeDate = mCal_Current.getTime();
			mSubObj.MOByDay = 0;
			mSubObj.ChargeMark = LocalConfig.RegMark;
			mSubObj.WeekMark = LocalConfig.RegMark;
			mSubObj.CodeByDay = LocalConfig.RegMark / LocalConfig.MarkPerCode;
			mSubObj.TotalCode = LocalConfig.RegMark / LocalConfig.MarkPerCode;

			mSubObj.MatchID = mMatchObj.MatchID;
			mSubObj.AnswerKQ = "";
			mSubObj.AnswerBT = "";
			mSubObj.AnswerGB = "";
			mSubObj.AnswerTS = "";
			mSubObj.AnswerTV = "";

			mSubObj.CofirmDeregDate = null;
			mSubObj.IsNotify = true;
			mSubObj.PartnerID = PartnerID;
		}
		catch (Exception ex)
		{
			throw ex;
		}

	}

	/**
	 * Tạo dữ liệu cho một đăng ký mới
	 * 
	 * @throws Exception
	 */
	private void CreateNewReg() throws Exception
	{
		try
		{
			mSubObj.MSISDN = mMsgObject.getUserid();
			mSubObj.FirstDate = mCal_Current.getTime();
			mSubObj.EffectiveDate = mCal_Current.getTime();
			mSubObj.ExpiryDate = mCal_Expire.getTime();

			mSubObj.ChargeDate = null;
			mSubObj.ChannelTypeID = mMsgObject.getChannelType();
			mSubObj.StatusID = dat.sub.Subscriber.Status.Active.GetValue();
			mSubObj.PID = MyConvert.GetPIDByMSISDN(mSubObj.MSISDN, LocalConfig.MAX_PID);
			mSubObj.MOByDay = 0;
			mSubObj.ChargeMark = LocalConfig.RegMark;
			mSubObj.WeekMark = LocalConfig.RegMark;
			mSubObj.CodeByDay = LocalConfig.RegMark / LocalConfig.MarkPerCode;
			mSubObj.TotalCode = LocalConfig.RegMark / LocalConfig.MarkPerCode;
			mSubObj.MatchID = mMatchObj.MatchID;
			mSubObj.AnswerKQ = "";
			mSubObj.AnswerBT = "";
			mSubObj.AnswerGB = "";
			mSubObj.AnswerTS = "";
			mSubObj.AnswerTV = "";

			mSubObj.IsNotify = true;

			mSubObj.CofirmDeregDate = null;

			mSubObj.IsDereg = false;
			mSubObj.LastUpdate = mCal_Current.getTime();
			mSubObj.PartnerID = PartnerID;
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

	protected Collection<MsgObject> getMessages(MsgObject msgObject, Keyword keyword) throws Exception
	{
		try
		{
			// Khoi tao
			Init(msgObject, keyword);

			// Lấy trận đấu đang diễn ra
			mMatchObj = Common.GetCurrentMatch(mCal_SendMO.getTime());

			if (mMatchObj.IsNull())
			{
				// nếu không có trận đầu nào đang diễn ra, thì lấy trận
				// đấu tiếp theo
				mMatchObj = Common.GetNextMatch();
			}

			// Lấy đối tác dựa vào Keyword.
			PartnerID = mKeyword.GetPartnerID(msgObject.getKeyword());

			/*
			 * if (mMatchObj.IsNull()) {
			 * mLog.log.info("Tran dau khong khong ton tai."); mMTType =
			 * MTType.Invalid; return AddToList(); }
			 */

			Integer PID = MyConvert.GetPIDByMSISDN(mMsgObject.getUserid(), LocalConfig.MAX_PID);

			// Lấy thông tin khách hàng đã đăng ký
			MyTableModel mTable_Sub = mSub.Select(2, PID.toString(), mMsgObject.getUserid());

			mSubObj = SubscriberObject.Convert(mTable_Sub, false);

			if (mSubObj.IsNull())
			{
				mTable_Sub = mUnSub.Select(2, PID.toString(), mMsgObject.getUserid());

				if (mTable_Sub.GetRowCount() > 0) mSubObj = SubscriberObject.Convert(mTable_Sub, true);
			}

			mSubObj.PID = MyConvert.GetPIDByMSISDN(mMsgObject.getUserid(), LocalConfig.MAX_PID);

			// Đăng ký mới (chưa từng đăng ký trước đây)
			if (mSubObj.IsNull())
			{
				// Tạo dữ liệu cho đăng ký mới
				CreateNewReg();

				ErrorCode mResult = mCharge.ChargeRegFree(PartnerID, mMsgObject.getUserid(), mMsgObject.getKeyword(),
						MyConfig.ChannelType.FromInt(mMsgObject.getChannelType()));

				if (mResult != ErrorCode.ChargeSuccess)
				{
					mMTType = MTType.RegFail;
					return AddToList();
				}

				if (Insert_Sub())
				{
					mMTType = MTType.RegNewSuccess;
				}
				else
				{
					mMTType = MTType.RegFail;
				}

				return AddToList();
			}

			// Nếu đã đăng ký rồi và tiếp tục đăng ký
			if (!mSubObj.IsNull() && mSubObj.IsDereg == false)
			{
				// Kiểm tra còn free hay không
				if (mSubObj.IsFreeReg())
				{
					mMTType = MTType.RegRepeatFree;
					return AddToList();
				}
				else
				{
					mMTType = MTType.RegRepeatNotFree;
					return AddToList();
				}
			}

			// Nếu trước đó số điện thoại đã được Hủy thuê bao
			if (mSubObj.IsDereg && mSubObj.StatusID == dat.sub.Subscriber.Status.UndoSub.GetValue())
			{
				CreateRegAgain();
				ErrorCode mResult = mCharge.ChargeRegFree(PartnerID, mMsgObject.getUserid(), mMsgObject.getKeyword(),
						MyConfig.ChannelType.FromInt(mMsgObject.getChannelType()));
				if (mResult != ErrorCode.ChargeSuccess)
				{
					mMTType = MTType.RegFail;
					return AddToList();
				}

				if (MoveToUnSub())
				{
					mMTType = MTType.RegNewSuccess;
				}
				else
				{
					mMTType = MTType.RegFail;
				}

				return AddToList();
			}
			// Đã đăng ký trước đó nhưng đang hủy
			if (mSubObj.IsDereg)
			{
				CreateRegAgain();

				// đồng bộ thuê bao sang Vinpahone
				ErrorCode mResult = mCharge.ChargeReg(PartnerID, mMsgObject.getUserid(), mMsgObject.getKeyword(),
						MyConfig.ChannelType.FromInt(mMsgObject.getChannelType()));

				// Charge
				if (mResult == ErrorCode.BlanceTooLow)
				{
					mMTType = MTType.RegNotEnoughMoney;
					return AddToList();
				}
				if (mResult != ErrorCode.ChargeSuccess)
				{
					mMTType = MTType.RegFail; // Đăng ký lại nhưng mất tiền
					return AddToList();
				}

				// Nếu xóa unsub hoặc Insert sub không thành công thì thông
				// báo lỗi
				if (MoveToUnSub())
				{
					mMTType = MTType.RegAgainSuccessNotFree;
					return AddToList();
				}

				mMTType = MTType.RegFail; // Đăng ký lại nhưng mất tiền
				return AddToList();

			}

			mMTType = MTType.RegFail;
			return AddToList();
		}
		catch (Exception ex)
		{
			mLog.log.error(Common.GetStringLog(msgObject), ex);
			mMTType = MTType.RegFail;
			return AddToList();
		}
		finally
		{
			// Insert vao log
			Insert_MOLog();

			mLog.log.debug(Common.GetStringLog(mMsgObject));
		}
	}
}
