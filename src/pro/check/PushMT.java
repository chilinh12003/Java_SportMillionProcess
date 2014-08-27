package pro.check;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Vector;

import pro.define.PushMTObject;
import pro.server.Common;
import pro.server.Program;
import pro.server.LocalConfig;
import uti.utility.MyConfig;
import uti.utility.MyConvert;
import uti.utility.MyLogger;
import dat.service.DefineMT.MTType;
import dat.service.MOLog;
import dat.service.News;
import dat.service.News.NewsType;
import dat.service.NewsObject;
import dat.sub.Subscriber;
import dat.sub.SubscriberObject;
import db.define.MyDataRow;
import db.define.MyTableModel;

public class PushMT extends Thread
{
	MyLogger mLog = new MyLogger(LocalConfig.LogConfigPath,this.getClass().toString());

	public PushMTObject mPushMTObj = new PushMTObject();

	Subscriber mSub = null;
	MOLog mMOLog = null;
	MyTableModel mTable_MOLog = null;

	public SimpleDateFormat DateFormat_InsertDB = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

	public PushMT()
	{

	}

	public PushMT(PushMTObject mPushMTObj)
	{
		this.mPushMTObj = mPushMTObj;

	}

	public void run()
	{
		if (Program.processData)
		{
			try
			{
				mSub = new Subscriber(LocalConfig.mDBConfig_MSSQL);
				mMOLog = new MOLog(LocalConfig.mDBConfig_MSSQL);
				mTable_MOLog = mMOLog.Select(0);

				PushForEach();

				UpdateNewsStatus(mPushMTObj.mNewsObj);
			}
			catch (Exception ex)
			{
				mLog.log.error("Loi xay ra trong qua trinh PUSH MT, Thead Index:" + mPushMTObj.ProcessIndex, ex);
			}
		}
	}

	private void UpdateNewsStatus(NewsObject mNewsObj)
	{
		try
		{
			News mNews = new News(LocalConfig.mDBConfig_MSSQL);
			MyTableModel mTable = mNews.Select(0);
			MyDataRow mRow = mTable.CreateNewRow();
			mRow.SetValueCell("NewsID", mNewsObj.NewsID);
			mRow.SetValueCell("StatusID", News.Status.Complete.GetValue());
			mRow.SetValueCell("StatusName", News.Status.Complete.toString());

			mTable.AddNewRow(mRow);
			mNews.Update(1, mTable.GetXML());

		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

	private boolean PushForEach() throws Exception
	{
		MyTableModel mTable = new MyTableModel(null, null);
		Vector<SubscriberObject> mList = new Vector<SubscriberObject>();
		try
		{
			Integer MinPID = 0;

			for (Integer PID = MinPID; PID <= LocalConfig.MAX_PID; PID++)
			{
				mPushMTObj.CurrentPID = PID;
				mPushMTObj.MaxOrderID = 0;

				mTable = GetSubscriber(PID);

				while (!mTable.IsEmpty())
				{
					mList = SubscriberObject.ConvertToList(mTable, false);

					for (SubscriberObject mSubObj : mList)
					{
						// Nếu bị dừng đột ngột
						if (!Program.processData)
						{
							Insert_MOLog();
							mLog.log.debug("Bi dung Charge: Charge Info:" + mPushMTObj.GetLogString(""));
							return false;
						}

						mPushMTObj.MaxOrderID = mSubObj.OrderID;

						// Nếu là bản tin reminder và khách hàng từ chối
						// nhận tin thì không bắn tin
						if (mPushMTObj.mNewsObj.mNewsType == NewsType.Reminder && mSubObj.IsNotify == false)
						{
							mLog.log.info("Pust MT KHONG PUSH DO KH TU CHOI NHAN TIN -->MSISDN:" + mSubObj.MSISDN
									+ "|NewsID:" + mPushMTObj.mNewsObj.NewsID);
							continue;
						}

						if (!SendMT(mSubObj))
						{
							mLog.log.info("Pust MT Fail -->MSISDN:" + mSubObj.MSISDN + "|NewsID:"
									+ mPushMTObj.mNewsObj.NewsID);
							MyLogger.WriteDataLog(LocalConfig.LogDataFolder, "_PushMT_NotSend",
									"PUSH MT FAIL --> MSISDN:" + mSubObj.MSISDN + "|NewsID:"
											+ mPushMTObj.mNewsObj.NewsID);
						}
						else
						{
							mLog.log.info("Pust MT OK -->MSISDN:" + mSubObj.MSISDN + "|NewsID:"
									+ mPushMTObj.mNewsObj.NewsID);
						}
						if(LocalConfig.TIME_DELAY_PUSH_MT > 0)
						{
							Thread.sleep(LocalConfig.TIME_DELAY_PUSH_MT);
						}
					}
					Insert_MOLog();
					mTable.Clear();
					mTable = GetSubscriber(PID);
				}
			}
			return true;
		}
		catch (Exception ex)
		{
			mLog.log.debug("Loi trong PUSH MT cho dich vu");
			throw ex;
		}
		finally
		{
			Insert_MOLog();

			// Cập nhật thời gian kết thúc bắn tin
			mPushMTObj.FinishDate = Calendar.getInstance().getTime();
			mLog.log.debug("KET THUC PUSH MT");
		}
	}

	private boolean SendMT(SubscriberObject mSubObj)
	{
		try
		{
			String REQUEST_ID = Long.toString(System.currentTimeMillis());
			if (Common.SendMT(mSubObj.MSISDN, "", mPushMTObj.mNewsObj.Content, REQUEST_ID))
			{
				AddToMOLog(mSubObj, MTType.PushMT, REQUEST_ID);
				return true;
			}
			return false;
		}
		catch (Exception ex)
		{
			mLog.log.error("Gui MT khong thanh cong: MSISDN:" + mSubObj.MSISDN + "||NewsID:"
					+ mPushMTObj.mNewsObj.NewsID, ex);
		}
		return false;
	}

	private void AddToMOLog(SubscriberObject mSubObj, MTType mMTType_Current, String RequestID) throws Exception
	{
		try
		{
			MyDataRow mRow_Log = mTable_MOLog.CreateNewRow();

			mRow_Log.SetValueCell("MSISDN", mSubObj.MSISDN);
			mRow_Log.SetValueCell("LogDate", DateFormat_InsertDB.format(Calendar.getInstance().getTime()));
			mRow_Log.SetValueCell("ChannelTypeID", MyConfig.ChannelType.SYSTEM.GetValue());
			mRow_Log.SetValueCell("ChannelTypeName", MyConfig.ChannelType.SYSTEM.toString());
			mRow_Log.SetValueCell("MTTypeID", mMTType_Current.GetValue());
			mRow_Log.SetValueCell("MTTypeName", mMTType_Current.toString());
			mRow_Log.SetValueCell("LogContent", "PushMT NewsID:" + mPushMTObj.mNewsObj.NewsID);
			mRow_Log.SetValueCell("MT", mPushMTObj.mNewsObj.Content);
			mRow_Log.SetValueCell("PID", MyConvert.GetPIDByMSISDN(mSubObj.MSISDN, LocalConfig.MAX_PID));
			mRow_Log.SetValueCell("RequestID", RequestID);

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
			if (mTable_MOLog.IsEmpty()) return;
			mMOLog.Insert(0, mTable_MOLog.GetXML());
			mTable_MOLog.Clear();
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
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
			return mSub.Select(5, mPushMTObj.RowCount.toString(), PID.toString(), mPushMTObj.MaxOrderID.toString(),
					mPushMTObj.ProcessNumber.toString(), mPushMTObj.ProcessIndex.toString());
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

}
