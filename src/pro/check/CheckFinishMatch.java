package pro.check;

import java.util.Calendar;

import pro.define.RandomCodeObject;
import pro.server.Common;
import pro.server.Program;
import pro.server.LocalConfig;
import uti.utility.MyLogger;
import dat.service.DefineCode;
import dat.service.Match;
import dat.service.MatchObject;
import dat.service.SubCode;
import db.define.MyDataRow;
import db.define.MyTableModel;

public class CheckFinishMatch extends Thread
{
	boolean IsRunning = false;
	MyLogger mLog = new MyLogger(LocalConfig.LogConfigPath,this.getClass().toString());

	public CheckFinishMatch()
	{

	}

	public void run()
	{
		while (Program.processData)
		{
			mLog.log.debug("---------------BAT DAU CHECK COMPUTE MATCH --------------------");
			try
			{
				boolean IsSameTime = false;
				for (String ChargeTime : LocalConfig.COMPUTE_LIST_TIME)
				{
					Calendar mCal_Current = Calendar.getInstance();
					if (mCal_Current.get(Calendar.HOUR_OF_DAY) != Integer.parseInt(ChargeTime))
					{
						continue;
					}
					IsSameTime = true;
				}

				if (IsSameTime && IsRunning == false)
				{
					MatchObject mMatchObj = Common.GetComputeMatch();

					if (!mMatchObj.IsNull() && mMatchObj.IsCompute == false)
					{
						IsRunning = true;

						/*
						 * //Tạo MDT RunThreadGenCode();
						 * 
						 * Thread.sleep(3000);
						 */
						// Chạy thread Push tin
						RunThreadCompute(mMatchObj);

						mMatchObj.IsCompute = true;

						// Update lại tính trạng cho match
						if (!UpdateMatch(mMatchObj))
						{
							mLog.log.info("COMPUTE MATCH - Khong cap nhat duoc Match: MatchID:" + mMatchObj.MatchID);
						}
					}
				}

			}
			catch (Exception ex)
			{
				mLog.log.error(ex);
			}

			IsRunning = false;

			try
			{
				mLog.log.debug("CHECK COMPUTE MATCH SE Delay " + LocalConfig.COMPUTE_TIME_DELAY + " Phut.");
				mLog.log.debug("---------------KET THUC CHECK COMPUTE --------------------");
				sleep(LocalConfig.COMPUTE_TIME_DELAY * 60 * 1000);
			}
			catch (InterruptedException ex)
			{
				mLog.log.error("Error Sleep thread", ex);
			}
		}
	}

	@SuppressWarnings("unused")
	private void RunThreadGenCode() throws Exception
	{
		DefineCode mDefineCode = new DefineCode(LocalConfig.mDBConfig_MSSQL);
		mDefineCode.Truncate(0);

		// Tạo List mã dự thưởng
		for (int j = 0; j < LocalConfig.COMPUTE_CREATECODE_PROCESS_NUMBER; j++)
		{
			GenerationCode mGenCode = new GenerationCode(j, LocalConfig.COMPUTE_CREATECODE_PROCESS_NUMBER);
			mGenCode.setPriority(Thread.MAX_PRIORITY);
			mGenCode.start();
			Thread.sleep(500);
		}
	}

	private boolean UpdateMatch(MatchObject mMatchObj) throws Exception
	{
		Match mMatch = new Match(LocalConfig.mDBConfig_MSSQL);
		MyTableModel mTable = mMatch.Select(0);
		MyDataRow mRow = mTable.CreateNewRow();
		mRow.SetValueCell("MatchID", mMatchObj.MatchID);
		mRow.SetValueCell("StatusID", Match.Status.Finish.GetValue());
		mRow.SetValueCell("StatusName", Match.Status.Finish.toString());
		mRow.SetValueCell("IsCompute", mMatchObj.IsCompute ? 1 : 0);

		mTable.AddNewRow(mRow);
		return mMatch.Update(1, mTable.GetXML());
	}

	/**
	 * Gửi MT cho khách hàng của dịch vụ
	 * 
	 * @param mServiceObject
	 * @param mNewsObject
	 */
	private void RunThreadCompute(MatchObject mMatchObj)
	{
		try
		{
			mLog.log.debug("-------------------------");
			mLog.log.debug("Bat dau Charging cho dich vu");

			// Lấy mincode
			RandomCodeObject.MinCode = GetMinCode();

			for (int j = 0; j < LocalConfig.COMPUTE_PROCESS_NUMBER; j++)
			{
				FinishMatch mFinishMatch = new FinishMatch();

				mFinishMatch.mFMObject.ProcessIndex = j;
				mFinishMatch.mFMObject.ProcessNumber = LocalConfig.COMPUTE_PROCESS_NUMBER;
				mFinishMatch.mFMObject.RowCount = LocalConfig.COMPUTE_ROWCOUNT;
				mFinishMatch.mFMObject.StartDate = Calendar.getInstance().getTime();
				mFinishMatch.mFMObject.mMatchObj = mMatchObj;
				Integer NumberPromotionDay = Common.GetNumberPromotionDay(Calendar.getInstance());
				mFinishMatch.mFMObject.NumberPromotion = NumberPromotionDay <= 0 ? 0 : NumberPromotionDay - 1;

				mFinishMatch.setPriority(Thread.MAX_PRIORITY);
				mFinishMatch.start();
				Thread.sleep(500);
			}
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

	/**
	 * Lấy min code. Nếu là ngày thứ 2 thì mincode = mincone trong file config
	 * Nếu là ngày != thứ 2 thì mincode = max mincode trong DB của ngày hôm qua
	 * 
	 * @return
	 */
	private Integer GetMinCode()
	{
		Integer MinCode = LocalConfig.MinCode;
		try
		{
			Calendar mCal_Current = Calendar.getInstance();

			if (mCal_Current.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY)
			{
				MinCode = LocalConfig.MinCode;
			}
			else
			{
				SubCode mSubCode = new SubCode(LocalConfig.mDBConfig_MSSQL);
				MyTableModel mTable = mSubCode.Select(3);
				if (mTable.IsEmpty() || mTable.GetRowCount() < 1 || mTable.GetValueAt(0, 0) == null) return MinCode;

				return Integer.parseInt(mTable.GetValueAt(0, 0).toString());
			}
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);

		}
		return MinCode;
	}

}
