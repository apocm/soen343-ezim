package org.ezim.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Hashtable;

import org.ezim.ui.EzimFileIn;
import org.ezim.ui.EzimFileOut;
import org.ezim.ui.EzimMsgIn;

public class EzimReceiver {
	
	private static Hashtable<String, String> getHeader(File fIn)
		throws Exception{
		
		Hashtable<String, String> htOut = null;
		FileInputStream fisHdr = new FileInputStream(fIn);
		byte[] bBuf = null;
		String strHdr = null;
		String[] arrLines = null;
		String[] arrHdrFldParts = null;

		htOut = new Hashtable<String, String>();

		try{
			fisHdr = new FileInputStream(fIn);
			bBuf = new byte[fisHdr.available()];
			fisHdr.read(bBuf);
		}
		finally{
			fisHdr.close();
		}

		strHdr = new String(bBuf, Ezim.dtxMsgEnc);
		arrLines = strHdr.split(EzimDtxSemantics.CRLF);

		for(int iX = 0; iX < arrLines.length; iX ++){
			arrHdrFldParts = arrLines[iX].split(EzimDtxSemantics.HDRSPR, 2);

			if (arrHdrFldParts.length > 1)
				htOut.put(arrHdrFldParts[0], arrHdrFldParts[1]);
		}

		return htOut;
	}

	private static void getMsg(InputStream isIn, int iCLen, EzimContact ecIn, String strSbj)
		throws Exception{
		
		byte[] bBuf = new byte[iCLen];
		int iTmp = 0;
		int iCnt = 0;
		String strTmp = null;

		while (! (iTmp < 0) && iCnt < iCLen){
			iTmp = isIn.read();

			if (! (iTmp < 0))
			{
				bBuf[iCnt] = (byte) iTmp;
				iCnt ++;
			}
		}

		strTmp = new String(bBuf, 0, iCnt, Ezim.dtxMsgEnc);

		new EzimMsgIn(ecIn, strSbj, strTmp);
	}

	private static void getFile(InputStream isIn, long lCLen, EzimFileIn efiIn)
		throws Exception{
		
		FileOutputStream fosTmp = null;
		byte[] bBuf = new byte[Ezim.dtxBufLen];
		int iTmp = 0;
		long lCnt = 0;

		try{
			fosTmp = new FileOutputStream(efiIn.getFile());
			efiIn.setSize(lCLen);
			efiIn.setProgressed(lCnt);

			while(! ((iTmp = isIn.read(bBuf)) < 0) && lCnt < lCLen){
				fosTmp.write(bBuf, 0, iTmp);
				lCnt += iTmp;
				efiIn.setProgressed(lCnt);
			}

			fosTmp.flush();
		}
		catch(SocketException se){
			// connection closed by remote
		}
		finally{
			if (fosTmp != null) fosTmp.close();

			String strSysMsg = null;

			if (lCnt < lCLen)
				strSysMsg = EzimLang.TransmissionAbortedByRemote;
			else if (lCnt == lCLen)
				strSysMsg = EzimLang.Done;

			efiIn.endProgress(strSysMsg);
		}
	}

	public static void parser(File fIn, Socket sckIn, EzimContact ecIn)
	{
		Hashtable<String, String> htHdr = null;
		InputStream isData = null;
		String strCType = null;
		String strCLen = null;
		long lCLen = 0;

		try{
			isData = sckIn.getInputStream();
			htHdr = EzimDtxSemantics.getHeader(fIn);
			strCType = htHdr.get(EzimDtxSemantics.HDR_CTYPE);
			strCLen = htHdr.get(EzimDtxSemantics.HDR_CLEN);

			if (strCType == null){
				throw new Exception("Header field \"Content-Type\" is missing.");
			}
			else if (strCLen == null)
			{
				throw new Exception("Header field \"Content-Length\" is missing.");
			}
			else
			{
				lCLen = Long.parseLong(strCLen);

				// receive incoming message
				if (strCType.equals(EzimDtxSemantics.CTYPE_MSG)){
					if (lCLen > (Ezim.maxMsgLength * 4)){
						throw new EzimException(
							"Illegally large incoming message from \""
							+ ecIn.getName()
							+ " (" + ecIn.getAddress().getHostAddress()
							+ ")\" detected."
						);
					}

					String strSbj = htHdr.get(EzimDtxSemantics.HDR_SBJ);

					EzimDtxSemantics.getMsg(isData, (int) lCLen, ecIn, strSbj);
				}
				// receive incoming file
				else if (strCType.equals(EzimDtxSemantics.CTYPE_FILE))
				{
					String strFileReqId = htHdr.get(EzimDtxSemantics.HDR_FILEREQID);

					EzimFileIn efiTmp = EzimFrxList.getInstance().get(strFileReqId);

					if (efiTmp != null){
						efiTmp.setSocket(sckIn);

						EzimDtxSemantics.getFile(isData, lCLen, efiTmp);
					}
				}
				// receive incoming file request
				else if (strCType.equals(EzimDtxSemantics.CTYPE_FILEREQ)){
					String strFilename = htHdr.get(EzimDtxSemantics.HDR_FILENAME);
					String strFileReqId = htHdr.get(EzimDtxSemantics.HDR_FILEREQID);
					String strFilesize = htHdr.get(EzimDtxSemantics.HDR_FILESIZE);

					EzimFileIn efiTmp = new EzimFileIn(ecIn, strFileReqId, strFilename);

					// this is just a previewed size and may different from
					// the actual one
					efiTmp.setSize(Long.parseLong(strFilesize));
				}
				// receive incoming file confirmation
				else if (strCType.equals(EzimDtxSemantics.CTYPE_FILECFM)){
					String strFileReqId = htHdr.get(EzimDtxSemantics.HDR_FILEREQID);
					String strFileCfm = htHdr.get(EzimDtxSemantics.HDR_FILECFM);

					EzimFileIn efiTmp = EzimFrxList.getInstance().get(strFileReqId);

					if (efiTmp != null){
						if (strFileCfm.equals(EzimDtxSemantics.OK)){
							efiTmp.setSysMsg(EzimLang.Receiving);
						}
						else
						{
							efiTmp.endProgress(EzimLang.TransmissionAbortedByRemote);
						}
					}
				}
				// receive incoming file response
				else if (strCType.equals(EzimDtxSemantics.CTYPE_FILERES)){
					String strFileReqId = htHdr.get(EzimDtxSemantics.HDR_FILEREQID);
					String strFileRes = htHdr.get(EzimDtxSemantics.HDR_FILERES);

					EzimFileOut efoTmp = EzimFtxList.getInstance().get(strFileReqId);

					EzimThreadPool etpTmp = EzimThreadPool.getInstance();

					if (efoTmp != null){
						// the remote user has accepted the request
						if (strFileRes.equals(EzimDtxSemantics.OK)){
							// everything looks fine
							if (efoTmp.getFile().exists()){
								efoTmp.setSysMsg(EzimLang.Sending);

								EzimFileConfirmer efcTmp= new EzimFileConfirmer(ecIn.getAddress(), ecIn.getPort(), strFileReqId, true);
								etpTmp.execute(efcTmp);

								EzimFileSender efsTmp = new EzimFileSender(efoTmp, ecIn.getAddress(), ecIn.getPort());
								etpTmp.execute(efsTmp);
							}
							// the file doesn't exist.  i.e. deleted
							else{
								efoTmp.endProgress(EzimLang.FileNotFoundTransmissionAborted);

								EzimFileConfirmer efcTmp= new EzimFileConfirmer(ecIn.getAddress(), ecIn.getPort(), strFileReqId, false);
								etpTmp.execute(efcTmp);
							}
						}
						// the remote user has refused the request
						else{
							efoTmp.endProgress(EzimLang.RefusedByRemote);
						}
					}
					// the outgoing file window is closed
					else if (strFileRes.equals(EzimDtxSemantics.OK)){
						EzimFileConfirmer efcTmp = new EzimFileConfirmer(ecIn.getAddress(), ecIn.getPort(), strFileReqId, false);
						etpTmp.execute(efcTmp);
					}
				}
			}
		}
		catch(EzimException ee){
			EzimLogger.getInstance().warning(ee.getMessage(), ee);
		}
		catch(Exception e){
			EzimLogger.getInstance().severe(e.getMessage(), e);
		}
		finally
		{
			try{
				if (isData != null) isData.close();
			}
			catch(Exception e){
				EzimLogger.getInstance().severe(e.getMessage(), e);
			}
		}
	}
}
