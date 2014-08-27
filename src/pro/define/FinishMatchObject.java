package pro.define;

import java.util.Date;

import dat.service.MatchObject;


public class FinishMatchObject implements java.io.Serializable
{

	private static final long serialVersionUID = 1L;

	public MatchObject mMatchObj = new MatchObject();

	/**
	 * Sô ngày khuyến mại
	 */
	public Integer NumberPromotion = 0;

	public Integer CurrentPID = 1;

	public String MSISDN = "";

	/**
	 * Số lượng process Push MT được tạo ra
	 */
	public Integer ProcessNumber = 1;

	/**
	 * Thứ tự của 1 process
	 */
	public Integer ProcessIndex = 0;

	/**
	 * Số thứ tự (OrderID) trong table Subscriber, process sẽ lấy những record
	 * có OrderID >= MaxOrderID
	 */
	public Integer MaxOrderID = 0;

	/**
	 * Tổng số record mỗi lần lấy lên để xử lý
	 */
	public Integer RowCount = 10;

	/**
	 * Thời gian bắt đầu chạy thead
	 */
	public Date StartDate = null;

	/**
	 * Thời gian kết thúc chạy thead
	 */
	public Date FinishDate = null;

	public boolean IsNull()
	{
		if (MSISDN == "") return true;
		else return false;
	}

	public String GetLogString(String Suffix) throws Exception
	{
		try
		{
			if (IsNull()) return "";
			String Fomart = "MSISDN:%s || ProcessIndex:%s || CurrentPID:%s || MaxOrderID:%s || Suffix:%s";
			return String.format(Fomart, new Object[]
			{MSISDN, ProcessIndex, CurrentPID, MaxOrderID, Suffix});
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}
}
