package pro.check;

import java.util.Calendar;

import pro.define.ChargeThreadObject.ThreadStatus;
import pro.server.Program;
import pro.server.LocalConfig;
import uti.utility.MyLogger;

/**
 * Kiểm tra liên tục thời gian trả tin cho từng dịch vụ. Nếu tồn tại tin theo
 * đúng giờ trả tin của dịch vụ, thì tiến hành trả tin cho khách hàng
 * 
 * @author Administrator
 * 
 */
public class CheckCharge extends Thread
{
	MyLogger mLog = new MyLogger(LocalConfig.LogConfigPath,this.getClass().toString());

	public CheckCharge()
	{

	}

	public void run()
	{
		while (Program.processData)
		{
			mLog.log.debug("---------------BAT DAU CHECK CHARGING --------------------");

			try
			{

				for (String ChargeTime : LocalConfig.CHARGE_LIST_TIME)
				{
					if (ChargeTime.equalsIgnoreCase("")) continue;

					Calendar mCal_Current = Calendar.getInstance();
					if (mCal_Current.get(Calendar.HOUR_OF_DAY) != Integer.parseInt(ChargeTime))
					{
						continue;
					}

					// Chạy thread Push tin
					RunThreadCharge();
				}

			}
			catch (Exception ex)
			{
				mLog.log.error(ex);
			}
			try
			{
				mLog.log.debug("CHECK CHARGING SE Delay " + LocalConfig.CHARGE_TIME_DELAY + " Phut.");
				mLog.log.debug("---------------KET THUC CHECK PUSH --------------------");
				sleep(LocalConfig.CHARGE_TIME_DELAY * 60 * 1000);
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
	private void RunThreadCharge()
	{
		try
		{
			mLog.log.debug("-------------------------");
			mLog.log.debug("Bat dau Charging cho dich vu");

			boolean AllowDereg = true;

			for (String ChargeTime : LocalConfig.CHARGE_LIST_TIME_NOT_DEREG)
			{
				if (ChargeTime.equalsIgnoreCase("")) continue;
				Calendar mCal_Current = Calendar.getInstance();
				if (mCal_Current.get(Calendar.HOUR_OF_DAY) != Integer.parseInt(ChargeTime))
				{
					continue;
				}
				AllowDereg = false;
				break;
			}

			for (int j = 0; j < LocalConfig.CHARGE_PROCESS_NUMBER; j++)
			{
				ChargeRenew mChargeRenew = new ChargeRenew();
				mChargeRenew.mCTObject.mThreadStatus = ThreadStatus.Charging;
				mChargeRenew.mCTObject.ProcessIndex = j;
				mChargeRenew.mCTObject.ProcessNumber = LocalConfig.CHARGE_PROCESS_NUMBER;
				mChargeRenew.mCTObject.RowCount = LocalConfig.CHARGE_ROWCOUNT;
				mChargeRenew.mCTObject.StartDate = Calendar.getInstance().getTime();
				mChargeRenew.mCTObject.AllowDereg = AllowDereg;
				mChargeRenew.setPriority(Thread.MAX_PRIORITY);
				mChargeRenew.start();
				Thread.sleep(500);
			}
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

}
