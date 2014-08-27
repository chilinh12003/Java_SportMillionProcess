package pro.mo;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;

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
import dat.sub.Subscriber;
import dat.sub.SubscriberObject;
import dat.sub.UnSubscriber;
import db.define.MyDataRow;
import db.define.MyTableModel;

public class Deregister extends ContentAbstract
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
	MyTableModel mTable_Sub = null;
	MyTableModel mTable_UnSub = null;

	DefineMT.MTType mMTType = MTType.RegFail;

	String MTContent = "";
	Integer PartnerID = 0;

	pro.charge.Charge mCharge = new Charge();

	private void Init(MsgObject msgObject, Keyword keyword) throws Exception
	{
		try
		{
			mSub = new Subscriber(LocalConfig.mDBConfig_MSSQL);
			mUnSub = new UnSubscriber(LocalConfig.mDBConfig_MSSQL);
			mMOLog = new MOLog(LocalConfig.mDBConfig_MSSQL);

			mTable_MOLog = mMOLog.Select(0);
			mTable_Sub = mSub.Select(0);
			mTable_UnSub = mUnSub.Select(0);

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
			MTContent = Common.GetDefineMT_Message(mSubObj, mMatchObj, mMTType);
			if (!MTContent.equalsIgnoreCase(""))
			{
				mMsgObject.setUsertext(MTContent);
				mMsgObject.setContenttype(LocalConfig.LONG_MESSAGE_CONTENT_TYPE);
				mMsgObject.setMsgtype(1);

				ListMessOject.add(new MsgObject(mMsgObject));
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
			mTable_UnSub.Clear();

			// Tạo row để insert vào Table Sub
			MyDataRow mRow_Sub = mTable_UnSub.CreateNewRow();
			mRow_Sub.SetValueCell("MSISDN", mSubObj.MSISDN);

			mRow_Sub.SetValueCell("FirstDate", MyConfig.Get_DateFormat_InsertDB().format(mSubObj.FirstDate));
			mRow_Sub.SetValueCell("EffectiveDate", MyConfig.Get_DateFormat_InsertDB().format(mSubObj.EffectiveDate));
			mRow_Sub.SetValueCell("ExpiryDate", MyConfig.Get_DateFormat_InsertDB().format(mSubObj.ExpiryDate));

			mRow_Sub.SetValueCell("RetryChargeCount", mSubObj.RetryChargeCount);

			if (mSubObj.RetryChargeDate != null)
				mRow_Sub.SetValueCell("RetryChargeDate",
						MyConfig.Get_DateFormat_InsertDB().format(mSubObj.RetryChargeDate));

			if (mSubObj.ChargeDate != null)
				mRow_Sub.SetValueCell("ChargeDate", MyConfig.Get_DateFormat_InsertDB().format(mSubObj.ChargeDate));

			if (mSubObj.RenewChargeDate != null)
				mRow_Sub.SetValueCell("RenewChargeDate",
						MyConfig.Get_DateFormat_InsertDB().format(mSubObj.RenewChargeDate));

			mRow_Sub.SetValueCell("ChannelTypeID", mSubObj.ChannelTypeID);
			mRow_Sub.SetValueCell("StatusID", mSubObj.StatusID);
			mRow_Sub.SetValueCell("PID", mSubObj.PID);
			mRow_Sub.SetValueCell("OrderID", mSubObj.OrderID);

			mRow_Sub.SetValueCell("MOByDay", mSubObj.MOByDay);
			mRow_Sub.SetValueCell("ChargeMark", mSubObj.ChargeMark);
			mRow_Sub.SetValueCell("WeekMark", mSubObj.WeekMark);
			mRow_Sub.SetValueCell("CodeByDay", mSubObj.CodeByDay);
			mRow_Sub.SetValueCell("TotalCode", mSubObj.TotalCode);
			mRow_Sub.SetValueCell("MatchID", mMatchObj.MatchID);
			mRow_Sub.SetValueCell("IsNotify", mSubObj.IsNotify);
			mRow_Sub.SetValueCell("AppID", mSubObj.AppID);
			mRow_Sub.SetValueCell("AppName", mSubObj.AppName);
			mRow_Sub.SetValueCell("UserName", mSubObj.UserName);
			mRow_Sub.SetValueCell("IP", mSubObj.IP);
			mRow_Sub.SetValueCell("PartnerID", mSubObj.PartnerID);

			if (mSubObj.LastUpdate != null)
				mRow_Sub.SetValueCell("LastUpdate", MyConfig.Get_DateFormat_InsertDB().format(mSubObj.LastUpdate));

			if (mSubObj.CofirmDeregDate != null)
				mRow_Sub.SetValueCell("CofirmDeregDate",
						MyConfig.Get_DateFormat_InsertDB().format(mSubObj.CofirmDeregDate));

			if (mSubObj.NotifyDate != null)
				mRow_Sub.SetValueCell("NotifyDate", MyConfig.Get_DateFormat_InsertDB().format(mSubObj.NotifyDate));

			if (mSubObj.DeregDate != null)
				mRow_Sub.SetValueCell("DeregDate", MyConfig.Get_DateFormat_InsertDB().format(mSubObj.DeregDate));

			mTable_UnSub.AddNewRow(mRow_Sub);
			return mTable_UnSub;
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

	private boolean MoveToSub() throws Exception
	{
		try
		{
			MyTableModel mTable_UnSub = AddInfo();

			if (!mUnSub.Move(0, mTable_UnSub.GetXML()))
			{
				mLog.log.info(" Move Tu Sub Sang UnSub KHONG THANH CONG: XML Insert-->" + mTable_UnSub.GetXML());
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
	private void CreateDeReg() throws Exception
	{
		try
		{
			mSubObj.ChannelTypeID = mMsgObject.getChannelType();
			mSubObj.DeregDate = mCal_Current.getTime();
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

			Integer PID = MyConvert.GetPIDByMSISDN(mMsgObject.getUserid(), LocalConfig.MAX_PID);

			MyTableModel mTable_Sub = mSub.Select(2, PID.toString(), mMsgObject.getUserid());

			if (mTable_Sub.GetRowCount() > 0) mSubObj = SubscriberObject.Convert(mTable_Sub, false);

			mSubObj.PID = MyConvert.GetPIDByMSISDN(mMsgObject.getUserid(), LocalConfig.MAX_PID);

			// Nếu chưa đăng ký dịch vụ
			if (mSubObj.IsNull())
			{
				mMTType = MTType.DeregNotRegister;
				return AddToList();
			}

			if (mSubObj.StatusID != dat.sub.Subscriber.Status.ConfirmDeregister.GetValue())
			{
				// Chưa hỏi confirm thì chưa thể hủy được
				mMTType = MTType.DeregNotSendConfirm;
				return AddToList();
			}

			CreateDeReg();

			if (ErrorCode.ChargeSuccess != mCharge.ChargeDereg(mSubObj.PartnerID, mMsgObject.getUserid(),
					mMsgObject.getKeyword(), MyConfig.ChannelType.FromInt(mMsgObject.getChannelType())))
			{
				MyLogger.WriteDataLog(LocalConfig.LogDataFolder, "_Charge_Sync_Dereg_VNP_FAIL", "DEREG FROM SMS --> "
						+ Common.GetStringLog(mMsgObject));
			}

			if (MoveToSub())
			{
				mMTType = MTType.DeregSuccess;
				return AddToList();
			}

			mMTType = MTType.DeregFail;

			return AddToList();
		}
		catch (Exception ex)
		{
			mLog.log.error(Common.GetStringLog(msgObject), ex);
			mMTType = MTType.DeregFail;
			return AddToList();
		}
		finally
		{
			AddToMOLog(mMTType, MTContent);

			// Insert vao log
			Insert_MOLog();

			mLog.log.debug(Common.GetStringLog(mMsgObject));
		}
	}

}
