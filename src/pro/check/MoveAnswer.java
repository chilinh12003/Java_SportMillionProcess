package pro.check;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Vector;

import pro.define.MoveAnswerObject;
import pro.server.Program;
import pro.server.LocalConfig;
import uti.utility.MyDate;
import uti.utility.MyLogger;
import dat.service.Answer;
import dat.service.Match;
import dat.sub.Subscriber;
import dat.sub.SubscriberObject;
import db.define.MyDataRow;
import db.define.MyTableModel;

public class MoveAnswer extends Thread
{
	MyLogger mLog = new MyLogger(LocalConfig.LogConfigPath,this.getClass().toString());

	public MoveAnswerObject mMoveObj = new MoveAnswerObject();

	public MoveAnswer()
	{

	}

	public MoveAnswer(MoveAnswerObject mMoveObj)
	{
		this.mMoveObj = mMoveObj;
	}

	Match mMatch = null;
	Subscriber mSub = null;
	Answer mAnswer = null;

	MyTableModel mTable_Answer = null;
	MyTableModel mTable_Sub = null;

	public SimpleDateFormat DateFormat_InsertDB = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

	Integer TotalCount = 0;
	Integer MaxCode = LocalConfig.MaxCode;

	Integer[] ArrCode = new Integer[500000];

	public void run()
	{
		if (Program.processData)
		{
			try
			{
				mMatch = new Match(LocalConfig.mDBConfig_MSSQL);
				mSub = new Subscriber(LocalConfig.mDBConfig_MSSQL);
				mAnswer = new Answer(LocalConfig.mDBConfig_MSSQL);

				mTable_Answer = mAnswer.Select(0);
				mTable_Sub = mSub.Select(0);

				PushForEach();
			}
			catch (Exception ex)
			{
				mLog.log.error("Loi xay ra trong qua trinh Move to Answer, Thead Index:" + mMoveObj.ProcessIndex, ex);
			}
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
				mMoveObj.CurrentPID = PID;
				mMoveObj.MaxOrderID = 0;

				mTable = GetSubscriber(PID);

				while (!mTable.IsEmpty())
				{
					mList = SubscriberObject.ConvertToList(mTable, false);

					for (SubscriberObject mSubObj : mList)
					{
						// Nếu bị dừng đột ngột
						if (!Program.processData)
						{
							Insert_Answer();
							UpdateToSub();
							mList.clear();

							mLog.log.debug("Bi dung Move to Answer Info:" + mMoveObj.GetLogString(""));
							return false;
						}

						mMoveObj.MaxOrderID = mSubObj.OrderID;

						// Nếu đang là chargefaile thì không được tính
						// điểm
						if (mSubObj.StatusID == dat.sub.Subscriber.Status.ChargeFail.GetValue())
						{
							AddToSub(mSubObj);
							continue;
						}
						
						Calendar mCal_Current = Calendar.getInstance();
						// Calendar mCal_EffectDate = Calendar.getInstance();
						Calendar mCal_ExpireDate = Calendar.getInstance();

						// mCal_EffectDate.setTime(mSubObj.EffectiveDate);
						mCal_ExpireDate.setTime(mSubObj.ExpiryDate);

						Long CountDay = MyDate.diffDays(mCal_ExpireDate, mCal_Current);
						if (CountDay != 1)
						{
							AddToSub(mSubObj);
							// nếu là đăng ký trong ngày thì không tiến
							// hành
							continue;
						}

						if (mSubObj.ChargeMark == 0 && mSubObj.AnswerBT.isEmpty() && mSubObj.AnswerGB.isEmpty()
								&& mSubObj.AnswerKQ.isEmpty() && mSubObj.AnswerTS.isEmpty()
								&& mSubObj.AnswerTV.isEmpty())
						{
							AddToSub(mSubObj);
							continue;
						}

						//Nếu là thứ 2 và khoảng thời gian là từ 0h-3h sáng thì reset lại điểm trong tuần
						Integer DayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
						
						if(DayOfWeek == Calendar.TUESDAY)
						{
							mSubObj.WeekMark = 0;
						}
						
						TotalCount++;
						AddToAnswer(mSubObj);
						AddToSub(mSubObj);
						
					}
					
					mLog.log.debug("Move to Answer thanh cong cho " + TotalCount + " Thue bao ProcessIndex:"
							+ mMoveObj.ProcessIndex);

					UpdateToSub();
					mList.clear();

					Insert_Answer();

					mTable.Clear();
					mTable = GetSubscriber(PID);
				}
			}
			return true;
		}
		catch (Exception ex)
		{
			mLog.log.debug("Loi trong Move to Answer");
			throw ex;
		}
		finally
		{
			Insert_Answer();

			UpdateToSub();
			mList.clear();

			// Cập nhật thời gian kết thúc bắn tin
			mMoveObj.FinishDate = Calendar.getInstance().getTime();
			mLog.log.debug("KET THUC MOVE ANSWER");
		}
	}

	private void AddToSub(SubscriberObject mSubObj) throws Exception
	{
		mSubObj.MOByDay = 0;
		mSubObj.ChargeMark = 0;
		
		mSubObj.CodeByDay = 0;
		mSubObj.MatchID = 0;
		mSubObj.AnswerBT = "";
		mSubObj.AnswerGB = "";
		mSubObj.AnswerKQ = "";
		mSubObj.AnswerTS = "";
		mSubObj.AnswerTV = "";
		mSubObj.LastUpdate = Calendar.getInstance().getTime();

		MyDataRow mRow = mTable_Sub.CreateNewRow();
		mRow.SetValueCell("MSISDN", mSubObj.MSISDN);
		mRow.SetValueCell("PID", mSubObj.PID);

		mRow.SetValueCell("MOByDay", mSubObj.MOByDay);
		mRow.SetValueCell("ChargeMark", mSubObj.ChargeMark);
		mRow.SetValueCell("WeekMark", mSubObj.WeekMark);
		mRow.SetValueCell("CodeByDay", mSubObj.CodeByDay);
		mRow.SetValueCell("TotalCode", mSubObj.TotalCode);
		mRow.SetValueCell("MatchID", mSubObj.MatchID);

		mRow.SetValueCell("AnswerKQ", mSubObj.AnswerKQ);
		mRow.SetValueCell("AnswerBT", mSubObj.AnswerBT);
		mRow.SetValueCell("AnswerGB", mSubObj.AnswerGB);
		mRow.SetValueCell("AnswerTS", mSubObj.AnswerTS);
		mRow.SetValueCell("AnswerTV", mSubObj.AnswerTV);

		if (mSubObj.LastUpdate != null)
			mRow.SetValueCell("LastUpdate", DateFormat_InsertDB.format(mSubObj.LastUpdate));

		mTable_Sub.AddNewRow(mRow);
	}

	private void UpdateToSub() throws Exception
	{
		try
		{
			if (mTable_Sub.GetRowCount() < 1) return;

			mSub.Update(4, mTable_Sub.GetXML());
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
		finally
		{
			mTable_Sub.Clear();
		}
	}

	private void AddToAnswer(SubscriberObject mSubObj) throws Exception
	{
		try
		{
			MyDataRow mRow_Answer = mTable_Answer.CreateNewRow();

			mRow_Answer.SetValueCell("MSISDN", mSubObj.MSISDN);
			mRow_Answer.SetValueCell("MatchID", mSubObj.MatchID);
			mRow_Answer.SetValueCell("AnswerKQ", mSubObj.AnswerKQ);
			mRow_Answer.SetValueCell("AnswerBT", mSubObj.AnswerBT);
			mRow_Answer.SetValueCell("AnswerGB", mSubObj.AnswerGB);
			mRow_Answer.SetValueCell("AnswerTS", mSubObj.AnswerTS);
			mRow_Answer.SetValueCell("AnswerTV", mSubObj.AnswerTV);
			mRow_Answer.SetValueCell("ChargeMark", mSubObj.ChargeMark);
			mRow_Answer.SetValueCell("WeekMark", mSubObj.WeekMark);
			mRow_Answer.SetValueCell("PID", mSubObj.PID);
			mRow_Answer.SetValueCell("OrderID", mSubObj.OrderID);
			mRow_Answer.SetValueCell("IsCompute", 0);
			if (mSubObj.LastUpdate != null)
				mRow_Answer.SetValueCell("LastUpdate", DateFormat_InsertDB.format(mSubObj.LastUpdate));

			mTable_Answer.AddNewRow(mRow_Answer);

		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

	private void Insert_Answer() throws Exception
	{
		try
		{
			if (mTable_Answer.IsEmpty()) return;

			mAnswer.Insert(0, mTable_Answer.GetXML());
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
		finally
		{
			mTable_Answer.Clear();
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
			return mSub.Select(5, mMoveObj.RowCount.toString(), PID.toString(), mMoveObj.MaxOrderID.toString(),
					mMoveObj.ProcessNumber.toString(), mMoveObj.ProcessIndex.toString());
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

}