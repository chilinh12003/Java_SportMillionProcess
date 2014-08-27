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
import dat.service.DefineMT;
import dat.service.DefineMT.MTType;
import dat.service.MOLog;
import dat.service.MatchObject;
import dat.sub.Subscriber;
import dat.sub.SubscriberObject;
import dat.sub.UnSubscriber;
import db.define.MyDataRow;
import db.define.MyTableModel;

public class Help extends ContentAbstract
{
	MyLogger mLog = new MyLogger(LocalConfig.LogConfigPath,this.getClass().toString());
	Collection<MsgObject> ListMessOject = new ArrayList<MsgObject>();

	MsgObject mMsgObject = null;
	SubscriberObject mSubObj = new SubscriberObject();

	MatchObject mMatchObj = new MatchObject();

	Calendar mCal_Current = Calendar.getInstance();
	Calendar mCal_SendMO = Calendar.getInstance();

	Subscriber mSub = null;
	UnSubscriber mUnSub = null;
	MOLog mMOLog = null;

	MyTableModel mTable_MOLog = null;

	DefineMT.MTType mMTType = MTType.Help;

	String MTContent = "";

	private void Init(MsgObject msgObject, Keyword keyword) throws Exception
	{
		try
		{
			mMOLog = new MOLog(LocalConfig.mDBConfig_MSSQL);

			mTable_MOLog = mMOLog.Select(0);

			mMsgObject = msgObject;
			mCal_SendMO.setTime(mMsgObject.getTTimes());
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
			String MTContent_Current = Common.GetDefineMT_Message(mSubObj, mMatchObj, MTType.Help);
			if (!MTContent_Current.equalsIgnoreCase(""))
			{
				// nếu đăng ký mới thành công, thì thêm 3 MT Notify vào
				mMsgObject.setUsertext(MTContent_Current);
				mMsgObject.setContenttype(LocalConfig.LONG_MESSAGE_CONTENT_TYPE);
				mMsgObject.setMsgtype(1);
				ListMessOject.add(new MsgObject(mMsgObject.clone()));
				AddToMOLog(MTType.Help, MTContent_Current);
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
			MOLog mMOLog = new MOLog(LocalConfig.mDBConfig_MSSQL);
			mMOLog.Insert(0, mTable_MOLog.GetXML());
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

	protected Collection<MsgObject> getMessages(MsgObject msgObject, Keyword keyword) throws Exception
	{
		try
		{
			Init(msgObject, keyword);
			// Lấy trận đấu đang diễn ra
			mMatchObj = Common.GetCurrentMatch(mCal_SendMO.getTime());

			if (mMatchObj.IsNull())
			{
				// nếu không có trận đầu nào đang diễn ra, thì lấy trận
				// đấu tiếp theo
				mMatchObj = Common.GetNextMatch();
			}

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
			mLog.log.debug(Common.GetStringLog(mMsgObject));
			Insert_MOLog();
		}
	}

}