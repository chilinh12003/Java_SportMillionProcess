package pro.define;

import java.util.Calendar;
import java.util.Date;

import uti.utility.MyDate;

public class PromotionDate
{
	public Calendar Begin = null;
	public Calendar End = null;
	public PromotionDate(Calendar Begin, Calendar End)
	{
		this.Begin = Begin;
		this.End = End;
	}

	public PromotionDate(Date Begin, Date End)
	{
		this.Begin = Calendar.getInstance();
		this.End = Calendar.getInstance();
		this.Begin.setTime(Begin);
		this.End.setTime(End);
	}

	/**
	 * Kiểm tra xem 1 ngày có nằm trong thời gian KM hay không
	 * 
	 * @param Current
	 */
	public boolean CheckPromotion(Calendar Current)
	{
		if (Current.after(Begin) && Current.before(End)) return true;
		else return false;
	}

	public Integer GetDay(Calendar Current) throws NumberFormatException, Exception
	{
		if (Current.after(Begin) && Current.before(End))
		{
			return Integer.parseInt(MyDate.SubDate(Begin, Current, Calendar.DATE).toString());
		}
		else return 0;
	}
}
