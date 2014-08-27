package pro.mo;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;

import pro.server.Common;
import pro.server.ContentAbstract;
import pro.server.Keyword;
import pro.server.LocalConfig;
import pro.server.MsgObject;
import uti.utility.MyConfig;
import uti.utility.MyConvert;
import uti.utility.MyLogger;
import uti.utility.MySeccurity;
import dat.service.DefineMT;
import dat.service.DefineMT.MTType;
import dat.service.MOLog;
import dat.service.MatchObject;
import dat.service.SubOTP;
import dat.sub.Subscriber;
import dat.sub.SubscriberObject;
import db.define.MyDataRow;
import db.define.MyTableModel;

public class GetOTP extends ContentAbstract
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
	MOLog mMOLog = null;

	MyTableModel mTable_MOLog = null;
	MyTableModel mTable_Sub = null;

	DefineMT.MTType mMTType = MTType.Fail;

	String MTContent = "";

	String Password = "";

	private void Init(MsgObject msgObject, Keyword keyword) throws Exception
	{
		try
		{
			mSub = new Subscriber(LocalConfig.mDBConfig_MSSQL);
			mMOLog = new MOLog(LocalConfig.mDBConfig_MSSQL);

			mTable_MOLog = mMOLog.Select(0);
			mTable_Sub = mSub.Select(0);

			mMsgObject = msgObject;
			mCal_SendMO.setTime(mMsgObject.getTTimes());
			Password = MySeccurity.CreateRamdomNumber(10000, 99999).toString();

			mCal_Expire.add(Calendar.HOUR_OF_DAY, 12);
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
			MTContent = Common.GetDefineMT_Message(mMTType);

			if (mMTType == MTType.GetOTPSuccess)
			{
				MTContent = MTContent.replace("[Password]", Password);
			}
			if (!MTContent.equalsIgnoreCase(""))
			{
				mMsgObject.setUsertext(MTContent);
				mMsgObject.setContenttype(LocalConfig.LONG_MESSAGE_CONTENT_TYPE);
				mMsgObject.setMsgtype(1);
				ListMessOject.add(new MsgObject(mMsgObject.clone()));
				AddToMOLog(mMTType, MTContent);
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

	private boolean InsertOTP(SubscriberObject mSubObj)
	{

		try
		{
			SubOTP mSubOTP = new SubOTP(LocalConfig.mDBConfig_MSSQL);
			MyTableModel mTable = mSubOTP.Select(0);
			MyDataRow mRow = mTable.CreateNewRow();
			mRow.SetValueCell("MSISDN", mSubObj.MSISDN);
			mRow.SetValueCell("Password", Password);
			mRow.SetValueCell("CreateDate", MyConfig.Get_DateFormat_InsertDB().format(mCal_Current.getTime()));
			mRow.SetValueCell("IsUse", "0");
			mRow.SetValueCell("ExpireDate", MyConfig.Get_DateFormat_InsertDB().format(mCal_Expire.getTime()));
			mRow.SetValueCell("LoginDate", "");
			mRow.SetValueCell("PID", mSubObj.PID);

			mTable.AddNewRow(mRow);

			return mSubOTP.CreateOTP(0, mTable.GetXML());
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
		return false;
	}

	protected Collection<MsgObject> getMessages(MsgObject msgObject, Keyword keyword) throws Exception
	{
		try
		{
			// Khoi tao
			Init(msgObject, keyword);

			Integer PID = MyConvert.GetPIDByMSISDN(mMsgObject.getUserid(), LocalConfig.MAX_PID);

			// Lấy thông tin khách hàng đã đăng ký
			MyTableModel mTable_Sub = mSub.Select(2, PID.toString(), mMsgObject.getUserid());

			mSubObj = SubscriberObject.Convert(mTable_Sub, false);

			if (mSubObj.IsNull())
			{
				// Không đăng ký mà trả lời
				mMTType = MTType.ConsultCodeNotReg;
				return AddToList();
			}

			if (!InsertOTP(mSubObj))
			{
				mMTType = MTType.Fail;
				return AddToList();
			}
			mMTType = MTType.GetOTPSuccess;
			return AddToList();
		}
		catch (Exception ex)
		{
			mLog.log.error(Common.GetStringLog(msgObject), ex);
			mMTType = MTType.SystemError;
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