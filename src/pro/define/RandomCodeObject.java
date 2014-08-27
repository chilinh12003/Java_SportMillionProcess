package pro.define;

import java.util.Random;
import java.util.Vector;

import pro.server.LocalConfig;
import uti.utility.MyLogger;

public class RandomCodeObject
{
	static MyLogger mLog = new MyLogger(LocalConfig.LogConfigPath,RandomCodeObject.class.toString());

	public static Integer MaxLenght = 10000;
	public static Vector<Object> ListCode = new Vector<Object>();
	public static Integer MinCode = 1000;

	public static Integer CurrentMaxCode = 1;

	public RandomCodeObject()
	{

	}

	public static boolean IsGetting = false;

	public static Vector<Integer> GetListCode(Integer NumberCode) throws Exception
	{
		synchronized (ListCode)
		{
			if (IsGetting) ListCode.wait();

			IsGetting = true;
			if (ListCode.size() < NumberCode + 5)
			{
				AddCode();
			}
			Vector<Integer> mList = new Vector<Integer>();
			while (NumberCode-- > 0)
			{
				Random mRand = new Random();
				Integer Index = mRand.nextInt(ListCode.size() - 1);
				Integer Code = (Integer) ListCode.get(Index);
				mList.add(Code);
				ListCode.remove((Object) Code);
			}
			IsGetting = false;
			ListCode.notify();
			return mList;
		}
	}

	/**
	 * Thêm các code vào list nếu code nhỏ hơn giá trị MaxLenght
	 * 
	 * @throws Exception
	 */
	private static void AddCode() throws Exception
	{
		if (ListCode.size() >= MaxLenght) return;

		if (CurrentMaxCode < MinCode) CurrentMaxCode = MinCode;

		int Count = ListCode.size();

		while (++Count <= MaxLenght)
		{
			CurrentMaxCode = CurrentMaxCode + 1;
			ListCode.add((Object) CurrentMaxCode);
		}
	}

}
