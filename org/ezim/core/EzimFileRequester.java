/*
    EZ Intranet Messenger
    Copyright (C) 2007  Chun-Kwong Wong <chunkwong.wong@gmail.com>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.ezim.core;

import java.io.File;
import java.lang.Thread;
import java.net.Socket;
import java.net.InetSocketAddress;
import javax.swing.JOptionPane;

import org.ezim.core.EzimDtxSemantics;
import org.ezim.core.EzimLang;

public class EzimFileRequester extends Thread
{
	private String ip = null;
	private String id = null;
	private File fOut = null;

	public EzimFileRequester
	(
		String strIp
		, String strId
		, File fIn
	)
	{
		this.ip = strIp;
		this.id = strId;
		this.fOut = fIn;
	}

	public void run()
	{
		Socket sckOut = null;
		InetSocketAddress isaTmp = null;

		try
		{
			sckOut = new Socket();
			isaTmp = new InetSocketAddress(this.ip, Ezim.dtxPort);
			sckOut.connect(isaTmp, Ezim.dtxTimeout);

			EzimDtxSemantics.sendFileReq(sckOut, this.id, this.fOut);
		}
		catch(Exception e)
		{
			JOptionPane.showMessageDialog
			(
				null
				, e.getMessage()
				, EzimLang.SendMessageError
				, JOptionPane.ERROR_MESSAGE
			);
		}
		finally
		{
			try
			{
				if (sckOut != null && ! sckOut.isClosed()) sckOut.close();
			}
			catch(Exception e)
			{
				// ignore
			}
		}
	}
}