package pro.check;

import java.util.Calendar;

import pro.server.Program;
import pro.server.LocalConfig;
import uti.utility.MyConfig;
import uti.utility.MyLogger;
import dat.service.News;
import dat.service.NewsObject;
import db.define.MyDataRow;
import db.define.MyTableModel;

public class CheckPushMT extends Thread
{
	MyLogger mLog = new MyLogger(LocalConfig.LogConfigPath, this.getClass().toString());

	public CheckPushMT()
	{

	}

	public void run()
	{
		while (Program.processData)
		{
			mLog.log.debug("---------------BAT DAU CHECK PUSH MT --------------------");

			try
			{
				for (String ChargeTime : LocalConfig.PUSHMT_LIST_TIME_NEWS)
				{
					Calendar mCal_Current = Calendar.getInstance();
					if (mCal_Current.get(Calendar.HOUR_OF_DAY) != Integer.parseInt(ChargeTime))
					{
						continue;
					}

					NewsObject mNewsObj = GetNews(News.NewsType.Push);
					if (mNewsObj.IsNull())
						continue;

					// Chạy thread Push tin
					RunThreadPushMT(mNewsObj);

					UpdateNewsStatus(mNewsObj);
				}

				for (String ChargeTime : LocalConfig.PUSHMT_LIST_TIME_REMINDER)
				{
					Calendar mCal_Current = Calendar.getInstance();
					if (mCal_Current.get(Calendar.HOUR_OF_DAY) != Integer.parseInt(ChargeTime))
					{
						continue;
					}

					NewsObject mNewsObj = GetNews(News.NewsType.Reminder);

					if (mNewsObj.IsNull())
						continue;

					// Chạy thread Push tin
					RunThreadPushMT(mNewsObj);

					UpdateNewsStatus(mNewsObj);
				}
			}
			catch (Exception ex)
			{
				mLog.log.error(ex);
			}
			try
			{
				mLog.log.debug("CHECK PUSHMT SE Delay " + LocalConfig.PUSHMT_TIME_DELAY + " Phut.");
				mLog.log.debug("---------------KET THUC CHECK PUSH MT--------------------");
				sleep(LocalConfig.PUSHMT_TIME_DELAY * 60 * 1000);
			}
			catch (InterruptedException ex)
			{
				mLog.log.error("Error Sleep thread", ex);
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
			mRow.SetValueCell("StatusID", News.Status.Sending.GetValue());
			mRow.SetValueCell("StatusName", News.Status.Sending.toString());

			mTable.AddNewRow(mRow);
			mNews.Update(1, mTable.GetXML());
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

	private NewsObject GetNews(News.NewsType mNewsType)
	{
		Calendar mCal_Begin = Calendar.getInstance();
		Calendar mCal_End = Calendar.getInstance();

		mCal_Begin.set(Calendar.MINUTE, 0);
		mCal_Begin.set(Calendar.SECOND, 0);

		mCal_End.set(Calendar.MINUTE, 59);
		mCal_End.set(Calendar.SECOND, 59);

		NewsObject mNewsObj = new NewsObject();
		try
		{

			News mNews = new News(LocalConfig.mDBConfig_MSSQL);
			MyTableModel mTable = mNews.Select(2, News.Status.New.GetValue().toString(), mNewsType.GetValue()
					.toString(), MyConfig.Get_DateFormat_InsertDB().format(mCal_Begin.getTime()), MyConfig
					.Get_DateFormat_InsertDB().format(mCal_End.getTime()));
			if (mTable.IsEmpty())
				return mNewsObj;
			mNewsObj = NewsObject.Convert(mTable);
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
		return mNewsObj;
	}

	/**
	 * Gửi MT cho khách hàng của dịch vụ
	 * 
	 * @param mServiceObject
	 * @param mNewsObject
	 */
	private void RunThreadPushMT(NewsObject mNewsObj)
	{
		try
		{
			mLog.log.debug("-------------------------");
			mLog.log.debug("Bat dau PUSH MT cho dich vu");

			// Số bản tin ngắn của MT được push di
			int ShortMTCount = mNewsObj.Content.length() / 160;
			if (mNewsObj.Content.length() % 160 > 0)
			{
				ShortMTCount++;
			}

			int DelaySendMT = 0;

			if (LocalConfig.PUSHMT_TPS > 0)
			{
				int TPS_Delay = (1000 / LocalConfig.PUSHMT_TPS) * LocalConfig.PUSHMT_PROCESS_NUMBER;
				DelaySendMT = ShortMTCount * TPS_Delay;
			}

			for (int j = 0; j < LocalConfig.PUSHMT_PROCESS_NUMBER; j++)
			{
				PushMT mPushMT = new PushMT();

				mPushMT.mPushMTObj.ProcessIndex = j;
				mPushMT.mPushMTObj.ProcessNumber = LocalConfig.PUSHMT_PROCESS_NUMBER;
				mPushMT.mPushMTObj.DelaySendMT = DelaySendMT;
				mPushMT.mPushMTObj.RowCount = LocalConfig.PUSHMT_ROWCOUNT;
				mPushMT.mPushMTObj.StartDate = Calendar.getInstance().getTime();
				mPushMT.mPushMTObj.mNewsObj = mNewsObj;
				mPushMT.setPriority(Thread.MAX_PRIORITY);
				mPushMT.start();
				Thread.sleep(DelaySendMT);
			}
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

}
