package pro.check;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Vector;

import pro.define.FinishMatchObject;
import pro.define.RandomCodeObject;
import pro.server.Common;
import pro.server.Program;
import pro.server.LocalConfig;
import uti.utility.MyConfig;
import uti.utility.MyLogger;
import dat.service.Answer;
import dat.service.AnswerLog;
import dat.service.AnswerObject;
import dat.service.DefineCode;
import dat.service.DefineMT.MTType;
import dat.service.MOLog;
import dat.service.Match;
import dat.service.SubCode;
import dat.sub.Subscriber;
import db.define.MyDataRow;
import db.define.MyTableModel;

public class FinishMatch extends Thread
{
	MyLogger mLog = new MyLogger(LocalConfig.LogConfigPath, this.getClass().toString());

	public FinishMatchObject mFMObject = new FinishMatchObject();

	public FinishMatch()
	{

	}

	public FinishMatch(FinishMatchObject mFMObject)
	{
		this.mFMObject = mFMObject;
	}

	Match mMatch = null;
	Answer mAnswer = null;
	SubCode mSubCode = null;
	MOLog mMOLog = null;
	AnswerLog mAnswerLog = null;
	Subscriber mSub = null;

	DefineCode mDefineCode = null;
	MyTableModel mTable_SubCode = null;
	MyTableModel mTable_Sub = null;
	MyTableModel mTable_MOLog = null;
	MyTableModel mTable_AnswerLog = null;
	MyTableModel mTable_Answer = null;

	public SimpleDateFormat DateFormat_InsertDB = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

	Integer TotalCount = 0;

	public void run()
	{
		if (Program.processData)
		{
			try
			{
				mMatch = new Match(LocalConfig.mDBConfig_MSSQL);
				mAnswer = new Answer(LocalConfig.mDBConfig_MSSQL);
				mSubCode = new SubCode(LocalConfig.mDBConfig_MSSQL);
				mDefineCode = new DefineCode(LocalConfig.mDBConfig_MSSQL);
				mMOLog = new MOLog(LocalConfig.mDBConfig_MSSQL);
				mAnswerLog = new AnswerLog(LocalConfig.mDBConfig_MSSQL);
				mAnswer = new Answer(LocalConfig.mDBConfig_MSSQL);

				mTable_AnswerLog = mAnswerLog.Select(0);
				mTable_MOLog = mMOLog.Select(0);
				mTable_SubCode = mSubCode.Select(0);
				mTable_Answer = mAnswer.Select(0);
				mTable_Sub = mSub.Select(0);

				PushForEach();
			}
			catch (Exception ex)
			{
				mLog.log.error("Loi xay ra trong qua trinh Finish Match, Thead Index:" + mFMObject.ProcessIndex, ex);
			}
		}
	}

	private boolean PushForEach() throws Exception
	{
		MyTableModel mTable = new MyTableModel(null, null);
		Vector<AnswerObject> mList = new Vector<AnswerObject>();
		try
		{
			Integer MinPID = 0;

			for (Integer PID = MinPID; PID <= LocalConfig.MAX_PID; PID++)
			{
				mFMObject.CurrentPID = PID;
				mFMObject.MaxOrderID = 0;

				mTable = GetSubscriber(PID);

				while (!mTable.IsEmpty())
				{
					mList = AnswerObject.ConvertToList(mTable);

					for (AnswerObject mAnswerObj : mList)
					{
						// Nếu bị dừng đột ngột
						if (!Program.processData)
						{
							UpdateToSub();
							UpdateToAnswer();
							mList.clear();
							mLog.log.debug("Bi dung Finish Match: Finish Match Info:" + mFMObject.GetLogString(""));
							return false;
						}

						mFMObject.MaxOrderID = mAnswerObj.OrderID;
						if (mAnswerObj.IsCompute)
							continue;

						TotalCount++;

						// Chỉ tính toan với những sub đang hoạt động.
						// Cẩn thận lúc lấy code và xóa code khi có
						// nhiều process chạy
						Integer ChargeMark = mAnswerObj.ChargeMark;
						Integer DayMark = 0;
						Integer WeekMark = mAnswerObj.WeekMark;
						Integer TotalCode = 0;
						Integer MarkBT = 0;
						Integer MarkGB = 0;
						Integer MarkKQ = 0;
						Integer MarkTS = 0;
						Integer MarkTV = 0;

						if (!mFMObject.mMatchObj.AnswerBT.isEmpty()
								&& mAnswerObj.AnswerBT.equalsIgnoreCase(mFMObject.mMatchObj.AnswerBT))
						{
							MarkBT = 500;
						}

						if (!mFMObject.mMatchObj.AnswerGB.isEmpty()
								&& mAnswerObj.AnswerGB.equalsIgnoreCase(mFMObject.mMatchObj.AnswerGB))
						{
							MarkGB = 500;
						}

						if (!mFMObject.mMatchObj.AnswerKQ.isEmpty()
								&& mAnswerObj.AnswerKQ.equalsIgnoreCase(mFMObject.mMatchObj.AnswerKQ))
						{
							MarkKQ = 500;
						}

						if (!mFMObject.mMatchObj.AnswerTS.isEmpty()
								&& mAnswerObj.AnswerTS.equalsIgnoreCase(mFMObject.mMatchObj.AnswerTS))
						{
							MarkTS = 500;
						}

						if (!mFMObject.mMatchObj.AnswerTV.isEmpty()
								&& mAnswerObj.AnswerTV.equalsIgnoreCase(mFMObject.mMatchObj.AnswerTV))
						{
							MarkTV = 500;
						}

						DayMark = ChargeMark + MarkBT + MarkGB + MarkKQ + MarkTS + MarkTV;

						TotalCode = DayMark / LocalConfig.MarkPerCode;
						WeekMark += DayMark;

						// WeekMarkWeek = mAnswerObj
						// Integer RetryCount = 1;
						// boolean ResulCreate = false;

						// Sẽ retry n lần nếu việc insert Subcode không
						// thành công
						// Vì có thể do nhiều process chạy nên khi select
						// DefineCode có thể bị trùng Code
						// Việc retry đảm bảo insert đầy đủ code cho khách
						// hàng
						/*
						 * while (!ResulCreate && RetryCount < 4 && WeekMark >
						 * 0) { ResulCreate = CreateSubCode(mAnswerObj,
						 * WeekMark, RetryCount++); }
						 */

						// if (ResulCreate)
						// {
						AddToAnswerLog(mAnswerObj, ChargeMark, DayMark, MarkBT, MarkGB, MarkKQ, MarkTS, MarkTV,
								TotalCode, WeekMark, 0);

						// Không dự đoán thì không gửi MT thông báo
						if (!mAnswerObj.AnswerBT.isEmpty() || !mAnswerObj.AnswerGB.isEmpty()
								|| !mAnswerObj.AnswerKQ.isEmpty() || !mAnswerObj.AnswerTS.isEmpty()
								|| !mAnswerObj.AnswerTV.isEmpty())
						{
							SendMT(mAnswerObj, DayMark, TotalCode, ChargeMark);
						}
						mAnswerObj.IsCompute = true;
						mAnswerObj.ComputeDate = Calendar.getInstance().getTime();
						AddToAnswer(mAnswerObj);

						AddToSub(mAnswerObj, WeekMark);
						// }
					}
					mLog.log.debug("Tao MDT thanh cong cho " + TotalCount + " Thue bao ProcessIndex:"
							+ mFMObject.ProcessIndex);

					UpdateToAnswer();
					mList.clear();

					Insert_AnswerLog();
					Insert_MOLog();

					mTable.Clear();
					mTable = GetSubscriber(PID);
					
					UpdateToSub();
					
				}
			}
			return true;
		}
		catch (Exception ex)
		{
			mLog.log.debug("Loi trong Finish Match cho dich vu");
			throw ex;
		}
		finally
		{
			UpdateToSub();
			UpdateToAnswer();
			mList.clear();

			// Cập nhật thời gian kết thúc bắn tin
			mFMObject.FinishDate = Calendar.getInstance().getTime();
			mLog.log.debug("KET THUC TINH DIEM");
		}
	}

	private void AddToSub(AnswerObject mAnswerObj, Integer WeekMark) throws Exception
	{
		MyDataRow mRow = mTable_Sub.CreateNewRow();

		mRow.SetValueCell("MSISDN", mAnswerObj.MSISDN);
		mRow.SetValueCell("PID", mAnswerObj.PID);
		mRow.SetValueCell("WeekMark", WeekMark);
		mTable_Answer.AddNewRow(mRow);

	}

	private void UpdateToSub() throws Exception
	{
		if (mTable_Sub.GetRowCount() < 1)
			return;

		mSub.Update(5, mTable_Sub.GetXML());

		mTable_Sub.Clear();
	}

	private void AddToAnswer(AnswerObject mAnswerObj) throws Exception
	{
		try
		{
			MyDataRow mRow_Answer = mTable_Answer.CreateNewRow();

			mRow_Answer.SetValueCell("MSISDN", mAnswerObj.MSISDN);
			mRow_Answer.SetValueCell("MatchID", mAnswerObj.MatchID);
			mRow_Answer.SetValueCell("AnswerKQ", mAnswerObj.AnswerKQ);
			mRow_Answer.SetValueCell("AnswerBT", mAnswerObj.AnswerBT);
			mRow_Answer.SetValueCell("AnswerGB", mAnswerObj.AnswerGB);
			mRow_Answer.SetValueCell("AnswerTS", mAnswerObj.AnswerTS);
			mRow_Answer.SetValueCell("AnswerTV", mAnswerObj.AnswerTV);
			mRow_Answer.SetValueCell("ChargeMark", mAnswerObj.ChargeMark);
			mRow_Answer.SetValueCell("PID", mAnswerObj.PID);
			mRow_Answer.SetValueCell("OrderID", mAnswerObj.OrderID);
			mRow_Answer.SetValueCell("IsCompute", mAnswerObj.IsCompute ? 1 : 0);
			if (mAnswerObj.LastUpdate != null)
				mRow_Answer.SetValueCell("LastUpdate", DateFormat_InsertDB.format(mAnswerObj.LastUpdate));

			if (mAnswerObj.ComputeDate != null)
				mRow_Answer.SetValueCell("ComputeDate", DateFormat_InsertDB.format(mAnswerObj.ComputeDate));

			mTable_Answer.AddNewRow(mRow_Answer);

		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

	private void UpdateToAnswer() throws Exception
	{
		try
		{
			if (mTable_Answer.GetRowCount() < 1)
				return;

			mAnswer.Update(1, mTable_Answer.GetXML());
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

	private boolean SendMT(AnswerObject mAnswerObj, Integer DayMark, Integer TotalCode, Integer ChargeMark)
			throws Exception
	{
		// Diem tra loi
		Integer AnswerMark = DayMark - mAnswerObj.ChargeMark;

		String MTContent = Common.GetDefineMT_Message(MTType.NotifyResult);
		MTContent = MTContent.replace("[Match]", mFMObject.mMatchObj.GetMatchName());
		MTContent = MTContent.replace("[PlayHour]", mFMObject.mMatchObj.GetPlayHour());
		MTContent = MTContent.replace("[PlayDate]", mFMObject.mMatchObj.GetPlayDate());

		MTContent = MTContent.replace("[AnswerMark]", AnswerMark.toString());
		MTContent = MTContent.replace("[TotalCode]", TotalCode.toString());
		MTContent = MTContent.replace("[CodeDate]", mFMObject.mMatchObj.GetCodeDate());
		MTContent = MTContent.replace("[ChargeMark]", ChargeMark.toString());
		String REQUEST_ID = Long.toString(System.currentTimeMillis());
		if (Common.SendMT(mAnswerObj.MSISDN, "", MTContent, REQUEST_ID))
		{
			AddToMOLog(mAnswerObj, MTContent, MTType.NotifyResult, REQUEST_ID);
			return true;
		}
		return false;
	}

	private boolean CreateSubCode(AnswerObject mAnswerObj, Integer WeekMark, Integer RetryCount)
	{
		try
		{
			Integer NumberCode = WeekMark / LocalConfig.MarkPerCode;

			Vector<Integer> mList = RandomCodeObject.GetListCode(NumberCode);

			Integer Count = 0;
			mTable_SubCode.Clear();
			// Lấy thêm mã nếu còn thiếu
			for (Integer Code : mList)
			{
				MyDataRow mRow = mTable_SubCode.CreateNewRow();
				mRow.SetValueCell("Code", Code);
				mRow.SetValueCell("MSISDN", mAnswerObj.MSISDN);
				mRow.SetValueCell("MatchID", mFMObject.mMatchObj.MatchID);
				mRow.SetValueCell("CurrentMark", WeekMark - 50 * Count);
				mRow.SetValueCell("CodeDate", DateFormat_InsertDB.format(mFMObject.mMatchObj.CodeDate));
				mRow.SetValueCell("CreateDate", DateFormat_InsertDB.format(Calendar.getInstance().getTime()));
				mRow.SetValueCell("PID", mAnswerObj.PID);
				mTable_SubCode.AddNewRow(mRow);
				Count++;
			}
			if (mSubCode.Insert(0, mTable_SubCode.GetXML()))
			{
				return true;
			}
			else
			{
				return false;
			}
		}
		catch (Exception ex)
		{
			mLog.log.error("Loi khi insert SubCode, RetryCount:" + RetryCount, ex);
			return false;
		}
		finally
		{
		}
	}

	private void AddToAnswerLog(AnswerObject mAnswerObj, Integer ChargeMark, Integer DayMark, Integer MarkBT,
			Integer MarkGB, Integer MarkKQ, Integer MarkTS, Integer MarkTV, Integer TotalCode, Integer WeekMark,
			Integer WeekMarkWeek) throws Exception
	{
		try
		{
			MyDataRow mRow_AnswerLog = mTable_AnswerLog.CreateNewRow();

			mRow_AnswerLog.SetValueCell("MSISDN", mAnswerObj.MSISDN);
			mRow_AnswerLog.SetValueCell("MatchID", mFMObject.mMatchObj.MatchID);
			mRow_AnswerLog.SetValueCell("CodeDate", DateFormat_InsertDB.format(mFMObject.mMatchObj.CodeDate));
			mRow_AnswerLog.SetValueCell("AnswerKQ", mAnswerObj.AnswerKQ);
			mRow_AnswerLog.SetValueCell("AnswerBT", mAnswerObj.AnswerBT);
			mRow_AnswerLog.SetValueCell("AnswerGB", mAnswerObj.AnswerGB);
			mRow_AnswerLog.SetValueCell("AnswerTS", mAnswerObj.AnswerTS);
			mRow_AnswerLog.SetValueCell("AnswerTV", mAnswerObj.AnswerTV);
			mRow_AnswerLog.SetValueCell("MarkKQ", MarkKQ);
			mRow_AnswerLog.SetValueCell("MarkBT", MarkBT);
			mRow_AnswerLog.SetValueCell("MarkGB", MarkGB);
			mRow_AnswerLog.SetValueCell("MarkTS", MarkTS);
			mRow_AnswerLog.SetValueCell("MarkTV", MarkTV);
			mRow_AnswerLog.SetValueCell("ChargeMark", ChargeMark);
			mRow_AnswerLog.SetValueCell("DayMark", DayMark);
			mRow_AnswerLog.SetValueCell("WeekMark", WeekMark);
			mRow_AnswerLog.SetValueCell("TotalCode", TotalCode);
			mRow_AnswerLog.SetValueCell("PID", mAnswerObj.PID);

			mTable_AnswerLog.AddNewRow(mRow_AnswerLog);

		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

	private void Insert_AnswerLog() throws Exception
	{
		try
		{
			if (mTable_AnswerLog.IsEmpty())
				return;

			mAnswerLog.Insert(0, mTable_AnswerLog.GetXML());
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
		finally
		{
			mTable_AnswerLog.Clear();
		}
	}

	private void AddToMOLog(AnswerObject mAnswerObj, String MTContent, MTType mMTType, String RequestID)
			throws Exception
	{
		try
		{
			MyDataRow mRow_Log = mTable_MOLog.CreateNewRow();

			mRow_Log.SetValueCell("MSISDN", mAnswerObj.MSISDN);

			mRow_Log.SetValueCell("LogDate", DateFormat_InsertDB.format(Calendar.getInstance().getTime()));
			mRow_Log.SetValueCell("ChannelTypeID", MyConfig.ChannelType.SYSTEM.GetValue());
			mRow_Log.SetValueCell("ChannelTypeName", MyConfig.ChannelType.SYSTEM.toString());
			mRow_Log.SetValueCell("MTTypeID", mMTType.GetValue());
			mRow_Log.SetValueCell("MTTypeName", mMTType.toString());
			mRow_Log.SetValueCell("MO", "");
			mRow_Log.SetValueCell("MT", MTContent);
			mRow_Log.SetValueCell("LogContent", "Send MT Result Match");
			mRow_Log.SetValueCell("PID", mAnswerObj.PID);
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
			if (mTable_MOLog.IsEmpty())
				return;

			mMOLog.Insert(0, mTable_MOLog.GetXML());

		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
		finally
		{
			mTable_MOLog.Clear();
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
			return mAnswer.Select(5, mFMObject.RowCount.toString(), PID.toString(), mFMObject.MaxOrderID.toString(),
					mFMObject.ProcessNumber.toString(), mFMObject.ProcessIndex.toString());
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

}