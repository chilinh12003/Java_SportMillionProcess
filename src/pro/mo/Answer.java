package pro.mo;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;

import pro.server.Common;
import pro.server.ContentAbstract;
import pro.server.Keyword;
import pro.server.LocalConfig;
import pro.server.MsgObject;
import uti.utility.MyCheck;
import uti.utility.MyConfig;
import uti.utility.MyConvert;
import uti.utility.MyLogger;
import dat.service.DefineMT;
import dat.service.DefineMT.MTType;
import dat.service.MOLog;
import dat.service.MatchObject;
import dat.sub.Subscriber;
import dat.sub.SubscriberObject;
import dat.sub.UnSubscriber;
import db.define.MyDataRow;
import db.define.MyTableModel;

public class Answer extends ContentAbstract
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

	MyTableModel mTable_MOLog = null;
	MyTableModel mTable_Sub = null;

	DefineMT.MTType mMTType = MTType.RegFail;

	String MTContent = "";

	// Giá trị khách hàng dự đoán
	String Value = "";

	private void Init(MsgObject msgObject, Keyword keyword) throws Exception
	{
		try
		{
			mSub = new Subscriber(LocalConfig.mDBConfig_MSSQL);
			mUnSub = new UnSubscriber(LocalConfig.mDBConfig_MSSQL);
			mMOLog = new MOLog(LocalConfig.mDBConfig_MSSQL);

			mTable_MOLog = mMOLog.Select(0);
			mTable_Sub = mSub.Select(0);

			mMsgObject = msgObject;
			Value = mMsgObject.getUsertext().toUpperCase().replace(mMsgObject.getKeyword().toUpperCase(), "");
			Value = Value.trim();

			mCal_SendMO.setTime(mMsgObject.getTTimes());
			mCal_Expire.set(mCal_Current.get(Calendar.YEAR), mCal_Current.get(Calendar.MONTH),
					mCal_Current.get(Calendar.DATE), 34, 59, 59);

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
			if (!MTContent.equalsIgnoreCase(""))
			{
				mMsgObject.setUsertext(MTContent);
				mMsgObject.setContenttype(LocalConfig.LONG_MESSAGE_CONTENT_TYPE);
				mMsgObject.setMsgtype(1);
				ListMessOject.add(new MsgObject(mMsgObject.clone()));
				AddToMOLog(mMTType, MTContent);
			}
			// Là tin nhắn thứ 10 và trả lời thành công
			if (mSubObj.LastUpdateIsToday() && mSubObj.MOByDay == LocalConfig.MaxAnswerByDay)
			{
				if (mMTType == MTType.AnswerKQ1 || mMTType == MTType.AnswerKQ2 || mMTType == MTType.AnswerKQ3
						|| mMTType == MTType.AnswerBT || mMTType == MTType.AnswerGB1 || mMTType == MTType.AnswerGB2
						|| mMTType == MTType.AnswerGB3 || mMTType == MTType.AnswerTS || mMTType == MTType.AnswerTV)
				{
					String MTContent_Current = Common.GetDefineMT_Message(mSubObj, mMatchObj, MTType.AnswerFinal);
					if (!MTContent_Current.equalsIgnoreCase(""))
					{
						mMsgObject.setUsertext(MTContent_Current);
						mMsgObject.setContenttype(LocalConfig.LONG_MESSAGE_CONTENT_TYPE);
						mMsgObject.setMsgtype(1);
						ListMessOject.add(new MsgObject(mMsgObject.clone()));
						AddToMOLog(MTType.AnswerFinal, MTContent_Current);
					}
				}
			}

			// Lấy các tinh nhắn HD cho các dự đoán mà khách hàng chưa
			// chơi
			if (mMTType == MTType.AnswerKQ1 || mMTType == MTType.AnswerKQ2 || mMTType == MTType.AnswerKQ3
					|| mMTType == MTType.AnswerBT || mMTType == MTType.AnswerGB1 || mMTType == MTType.AnswerGB2
					|| mMTType == MTType.AnswerGB3 || mMTType == MTType.AnswerTS || mMTType == MTType.AnswerTV)
			{

				MTType mMTType_Current = MTType.Default;
				if (mSubObj.AnswerBT == null || mSubObj.AnswerBT.equalsIgnoreCase(""))
				{
					mMTType_Current = MTType.AnswerGuideBT;
				}
				else if (mSubObj.AnswerGB == null || mSubObj.AnswerGB.equalsIgnoreCase(""))
				{
					mMTType_Current = MTType.AnswerGuideGB;
				}
				else if (mSubObj.AnswerKQ == null || mSubObj.AnswerKQ.equalsIgnoreCase(""))
				{
					mMTType_Current = MTType.AnswerGuideKQ;
				}
				else if (mSubObj.AnswerTS == null || mSubObj.AnswerTS.equalsIgnoreCase(""))
				{
					mMTType_Current = MTType.AnswerGuideTS;
				}
				else if (mSubObj.AnswerTV == null || mSubObj.AnswerTV.equalsIgnoreCase(""))
				{
					mMTType_Current = MTType.AnswerGuideTV;
				}

				if (mMTType_Current != MTType.Default)
				{
					String MTContent_Current = Common.GetDefineMT_Message(mSubObj, mMatchObj, mMTType_Current);
					if (!MTContent_Current.equalsIgnoreCase(""))
					{
						mMsgObject.setUsertext(MTContent_Current);
						mMsgObject.setContenttype(LocalConfig.LONG_MESSAGE_CONTENT_TYPE);
						mMsgObject.setMsgtype(1);
						ListMessOject.add(new MsgObject(mMsgObject.clone()));
						AddToMOLog(mMTType_Current, MTContent_Current);
					}
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
			mRow_Log.SetValueCell("PartnerID", mSubObj.PartnerID);
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
			mRow_Sub.SetValueCell("PID", mSubObj.PID);

			mRow_Sub.SetValueCell("MOByDay", mSubObj.MOByDay);
			mRow_Sub.SetValueCell("ChargeMark", mSubObj.ChargeMark);
			mRow_Sub.SetValueCell("WeekMark", mSubObj.WeekMark);
			mRow_Sub.SetValueCell("CodeByDay", mSubObj.CodeByDay);
			mRow_Sub.SetValueCell("TotalCode", mSubObj.TotalCode);
			mRow_Sub.SetValueCell("MatchID", mMatchObj.MatchID);

			mRow_Sub.SetValueCell("AnswerKQ", mSubObj.AnswerKQ);
			mRow_Sub.SetValueCell("AnswerBT", mSubObj.AnswerBT);
			mRow_Sub.SetValueCell("AnswerGB", mSubObj.AnswerGB);
			mRow_Sub.SetValueCell("AnswerTS", mSubObj.AnswerTS);
			mRow_Sub.SetValueCell("AnswerTV", mSubObj.AnswerTV);

			if (mSubObj.LastUpdate != null)
				mRow_Sub.SetValueCell("LastUpdate", MyConfig.Get_DateFormat_InsertDB().format(mSubObj.LastUpdate));

			mTable_Sub.AddNewRow(mRow_Sub);
			return mTable_Sub;
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

	private boolean UpdateToSub() throws Exception
	{
		try
		{
			MyTableModel mTable_Sub = AddInfo();

			if (!mSub.Update(1, mTable_Sub.GetXML()))
			{
				mLog.log.info("Update Answer vao table Subscriber KHONG THANH CONG: XML Insert-->"
						+ mTable_Sub.GetXML());
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
	private void CreateUpdate() throws Exception
	{
		try
		{
			if (mSubObj.LastUpdateIsToday())
			{
				mSubObj.MOByDay = mSubObj.MOByDay + 1;
			}
			else
			{
				mSubObj.MOByDay = 1;
				mSubObj.AnswerKQ = "";
				mSubObj.AnswerBT = "";
				mSubObj.AnswerGB = "";
				mSubObj.AnswerTS = "";
				mSubObj.AnswerTV = "";
			}

			mSubObj.LastUpdate = mCal_Current.getTime();
			mSubObj.MSISDN = mMsgObject.getUserid();
			mSubObj.PID = MyConvert.GetPIDByMSISDN(mSubObj.MSISDN, LocalConfig.MAX_PID);

			mSubObj.LastUpdate = mCal_Current.getTime();

			mSubObj.MatchID = mMatchObj.MatchID;

			if (mMTType == MTType.AnswerKQ1 || mMTType == MTType.AnswerKQ2 || mMTType == MTType.AnswerKQ3) mSubObj.AnswerKQ = Value;
			else if (mMTType == MTType.AnswerBT) mSubObj.AnswerBT = Value;
			else if (mMTType == MTType.AnswerGB1 || mMTType == MTType.AnswerGB2 || mMTType == MTType.AnswerGB3) mSubObj.AnswerGB = Value;
			else if (mMTType == MTType.AnswerTS) mSubObj.AnswerTS = Value;
			else if (mMTType == MTType.AnswerTV) mSubObj.AnswerTV = Value;
		}
		catch (Exception ex)
		{
			throw ex;
		}

	}

	/**
	 * Kiểm tra xem là loại câu trả lời nào
	 * 
	 * @return
	 * @throws Exception
	 */
	private MTType GetTypeAnswer() throws Exception
	{
		String AnswerKeyword = mMsgObject.getKeyword().toUpperCase();

		if (Value == null || Value.equalsIgnoreCase("")) return MTType.AnswerInvalid;

		if (AnswerKeyword.equalsIgnoreCase(LocalConfig.KeywordBT))
		{
			if (MyCheck.isNumeric(Value)) return MTType.AnswerBT;
			else
			{
				return MTType.AnswerInvalid;
			}
		}
		else if (AnswerKeyword.equalsIgnoreCase(LocalConfig.KeywordGB) && Value.equalsIgnoreCase("1"))
		{
			return MTType.AnswerGB1;
		}
		else if (AnswerKeyword.equalsIgnoreCase(LocalConfig.KeywordGB) && Value.equalsIgnoreCase("2"))
		{
			return MTType.AnswerGB2;
		}
		else if (AnswerKeyword.equalsIgnoreCase(LocalConfig.KeywordGB) && Value.equalsIgnoreCase("3"))
		{
			return MTType.AnswerGB3;
		}
		else if (AnswerKeyword.equalsIgnoreCase(LocalConfig.KeywordKQ) && Value.equalsIgnoreCase("1"))
		{
			return MTType.AnswerKQ1;
		}
		else if (AnswerKeyword.equalsIgnoreCase(LocalConfig.KeywordKQ) && Value.equalsIgnoreCase("2"))
		{
			return MTType.AnswerKQ2;
		}
		else if (AnswerKeyword.equalsIgnoreCase(LocalConfig.KeywordKQ) && Value.equalsIgnoreCase("3"))
		{
			return MTType.AnswerKQ3;
		}

		else if (AnswerKeyword.equalsIgnoreCase(LocalConfig.KeywordTS))
		{
			String[] Arr = Value.split(" ");
			if (Arr.length != 2) return MTType.AnswerInvalid;
			try
			{
				Integer TS_1 = Integer.parseInt(Arr[0]);
				Integer TS_2 = Integer.parseInt(Arr[1]);

				Value = TS_1 + "-" + TS_2;
			}
			catch (Exception ex)
			{
				return MTType.AnswerInvalid;
			}

			return MTType.AnswerTS;
		}
		else if (AnswerKeyword.equalsIgnoreCase(LocalConfig.KeywordTV))
		{
			if (MyCheck.isNumeric(Value)) return MTType.AnswerTV;
			else
			{
				return MTType.AnswerInvalid;
			}
		}
		else return MTType.AnswerInvalid;
	}

	protected Collection<MsgObject> getMessages(MsgObject msgObject, Keyword keyword) throws Exception
	{
		try
		{
			// Khoi tao
			Init(msgObject, keyword);

			// Lấy trận đấu đang diễn ra
			mMatchObj = Common.GetCurrentMatch(mCal_Current.getTime());

			if (mMatchObj.IsNull())
			{
				mLog.log.info("Tran dau khong khong ton tai.");
				mMTType = MTType.AnswerExpire;
				return AddToList();
			}

			mMTType = GetTypeAnswer();

			if (mMTType == MTType.AnswerInvalid)
			{
				mMTType = MTType.AnswerInvalid;
				return AddToList();
			}

			Integer PID = MyConvert.GetPIDByMSISDN(mMsgObject.getUserid(), LocalConfig.MAX_PID);

			// Lấy thông tin khách hàng đã đăng ký
			MyTableModel mTable_Sub = mSub.Select(2, PID.toString(), mMsgObject.getUserid());

			mSubObj = SubscriberObject.Convert(mTable_Sub, false);

			if (mSubObj.IsNull())
			{
				// Không đăng ký mà trả lời
				mMTType = MTType.AnswerNotReg;
				return AddToList();
			}

			if (mSubObj.StatusID == dat.sub.Subscriber.Status.ChargeFail.GetValue())
			{
				// Đang trong quá trình retry charge
				mMTType = MTType.AnswerNotExtend;
				return AddToList();
			}

			mSubObj.PID = MyConvert.GetPIDByMSISDN(mMsgObject.getUserid(), LocalConfig.MAX_PID);

			if (mSubObj.LastUpdateIsToday() && mSubObj.MOByDay >= LocalConfig.MaxAnswerByDay)
			{
				// Trả lời quá 10 lần trên 1 ngày
				mMTType = MTType.AnswerOver;
				return AddToList();
			}

			CreateUpdate();
			if (!UpdateToSub())
			{
				mMTType = MTType.RegFail;
			}
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