package pro.check;

import pro.server.Program;
import pro.server.LocalConfig;
import uti.utility.MyLogger;
import dat.service.DefineCode;
import db.define.MyDataRow;
import db.define.MyTableModel;

public class GenerationCode extends Thread
{
	MyLogger mLog = new MyLogger(LocalConfig.LogConfigPath,this.getClass().toString());

	public Integer ProcessIndex = 0;
	public Integer ProcessNumber = 1;

	public GenerationCode(Integer ProcessIndex, Integer ProcessNumber)
	{
		this.ProcessIndex = ProcessIndex;
		this.ProcessNumber = ProcessNumber;
	}

	public void run()
	{
		if (Program.processData)
		{
			mLog.log.debug("--------------- BAT DAU TAO CODE ----------------");

			try
			{
				DefineCode mDefineCode = new DefineCode(LocalConfig.mDBConfig_MSSQL);
				MyTableModel mTable = mDefineCode.Select(0);

				Integer Count = 1;
				Integer NumberCode = 0;
				for (int i = LocalConfig.MinCode; i < LocalConfig.MaxCode; i++)
				{
					if (!Program.processData) break;

					if (i % ProcessNumber != ProcessIndex) continue;

					MyDataRow mRow = mTable.CreateNewRow();
					mRow.SetValueCell("Code", i);
					mTable.AddNewRow(mRow);

					if (++Count == 500)
					{
						NumberCode += Count;

						Count = 0;
						if (mDefineCode.Insert(0, mTable.GetXML()))
						{
							// mLog.log.info("Tao xong " + NumberCode
							// +"|ProcessIndex:" +ProcessIndex
							// +"|ProcessNumber:"+ProcessNumber);
						}
						else
						{
							// mLog.log.info("Khong the insert xuong DefineCode: ProcessIndex:"
							// +ProcessIndex +"|ProcessNumber:"+ProcessNumber);
						}
						mTable.Clear();
					}
				}
				if (mTable.GetRowCount() > 0)
				{
					if (mDefineCode.Insert(0, mTable.GetXML()))
					{
						mLog.log.info("Tao xong " + NumberCode + "|ProcessIndex:" + ProcessIndex + "|ProcessNumber:"
								+ ProcessNumber);
					}
					else
					{
						mLog.log.info("Khong the insert xuong DefineCode: ProcessIndex:" + ProcessIndex
								+ "|ProcessNumber:" + ProcessNumber);
					}
				}
			}
			catch (Exception ex)
			{
				mLog.log.error(ex);
			}

			mLog.log.debug("---------------KET THUC TAO CODE ----------------");
		}
	}
}
