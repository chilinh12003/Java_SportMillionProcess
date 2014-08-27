package pro.check;

import java.util.Calendar;

import pro.server.Program;
import pro.server.LocalConfig;
import uti.utility.MyLogger;

public class CheckMoveAnswer extends Thread
{
	MyLogger mLog = new MyLogger(LocalConfig.LogConfigPath,this.getClass().toString());

	public CheckMoveAnswer()
	{

	}

	public void run()
	{
		while (Program.processData)
		{
			mLog.log.debug("---------------BAT DAU MOVE ANSWER --------------------");

			try
			{
				for (String ChargeTime : LocalConfig.MOVEANSWER_LIST_TIME)
				{
					Calendar mCal_Current = Calendar.getInstance();
					if (mCal_Current.get(Calendar.HOUR_OF_DAY) != Integer.parseInt(ChargeTime))
					{
						continue;
					}

					// Chạy thread Push tin
					RunThread();
				}

			}
			catch (Exception ex)
			{
				mLog.log.error(ex);
			}
			try
			{
				mLog.log.debug("CHECK MOVE ANSWER SE Delay " + LocalConfig.MOVEANSWER_TIME_DELAY + " Phut.");
				mLog.log.debug("---------------KET THUC MOVE ANSWER--------------------");
				sleep(LocalConfig.MOVEANSWER_TIME_DELAY * 60 * 1000);
			}
			catch (InterruptedException ex)
			{
				mLog.log.error("Error Sleep thread", ex);
			}
		}
	}

	/**
	 * Gửi MT cho khách hàng của dịch vụ
	 * 
	 * @param mServiceObject
	 * @param mNewsObject
	 */
	private void RunThread()
	{
		try
		{
			mLog.log.debug("-------------------------");
			mLog.log.debug("Bat dau Charging cho dich vu");

			for (int j = 0; j < LocalConfig.MOVEANSWER_PROCESS_NUMBER; j++)
			{
				MoveAnswer mMoveAnswer = new MoveAnswer();
				mMoveAnswer.mMoveObj.ProcessIndex = j;
				mMoveAnswer.mMoveObj.ProcessNumber = LocalConfig.MOVEANSWER_PROCESS_NUMBER;
				mMoveAnswer.mMoveObj.RowCount = LocalConfig.MOVEANSWER_ROWCOUNT;
				mMoveAnswer.mMoveObj.StartDate = Calendar.getInstance().getTime();

				mMoveAnswer.setPriority(Thread.MAX_PRIORITY);
				mMoveAnswer.start();
				Thread.sleep(500);
			}
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

}
