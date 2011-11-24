/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import java.util.ArrayList;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.os.Looper;
import com.android.internal.telephony.IccUtils;

public class AdnRecordLoader extends Handler {
	static String LOG_TAG;

	// ***** Instance Variables

	PhoneBase phone;
	int ef;
	int extensionEF;
	int pendingExtLoads;
	Message userResponse;
	String pin2;

	// add multi record and email in usim begin
	private IccFileHandler mFh;
	int emailEF;
	int iapEF;

	int sneEF;
	int aasEF;
	int grpEF;
	int gasEF;
	int fileCount;
	int emailNum;
	int adnNum;
	int emailNumInIap;
	int emailTagInIap;
	int aasNum;
	byte[] iapRec;
	ArrayList<Integer> anrefids;
	ArrayList<Integer> anrNums;
	ArrayList<Integer> emailEfids;
	ArrayList<Integer> emailNums;
      	ArrayList<Integer> emailTagInIaps;
	// add multi record and email in usim end

	// For "load one"
	int recordNumber;

	// for "load all"
	ArrayList<AdnRecord> adns; // only valid after EVENT_ADN_LOAD_ALL_DONE

	// Either an AdnRecord or a reference to adns depending
	// if this is a load one or load all operation
	Object result;

	// ***** Event Constants

	static final int EVENT_ADN_LOAD_DONE = 1;
	static final int EVENT_EXT_RECORD_LOAD_DONE = 2;
	static final int EVENT_ADN_LOAD_ALL_DONE = 3;
	static final int EVENT_EF_LINEAR_RECORD_SIZE_DONE = 4;
	static final int EVENT_UPDATE_RECORD_DONE = 5;

	// add multi record and email in usim begin
	static final int EVENT_EF_PBR_EMAIL_LINEAR_RECORD_SIZE_DONE = 6;
	static final int EVENT_EF_PBR_IAP_LINEAR_RECORD_SIZE_DONE = 7;

	static final int EVENT_EF_PBR_ANR_LINEAR_RECORD_SIZE_DONE = 8;
	static final int EVENT_EF_PBR_SNE_LINEAR_RECORD_SIZE_DONE = 9;
	static final int EVENT_UPDATE_ANR_RECORD_DONE = 10;
	static final int EVENT_EF_PBR_AAS_LINEAR_RECORD_SIZE_DONE = 11;
	static final int EVENT_EF_PBR_GRP_LINEAR_RECORD_SIZE_DONE = 12;
	static final int EVENT_EF_PBR_GAS_LINEAR_RECORD_SIZE_DONE = 13;
	static final int EVENT_UPDATE_AAS_RECORD_DONE = 14;
	static final int EVENT_UPDATE_SNE_RECORD_DONE = 15;
	static final int EVENT_UPDATE_GRP_RECORD_DONE = 16;
	static final int EVENT_UPDATE_GAS_RECORD_DONE = 17;

	// add multi record and email in usim end

	// ***** Constructor

	public AdnRecordLoader(PhoneBase phone) {
		// The telephony unit-test cases may create AdnRecords
		// in secondary threads
		super(phone.getHandler().getLooper());

		this.phone = phone;
		mFh = phone.getIccFileHandler();
		LOG_TAG = phone.getPhoneName();
	}

	// add multi record and email in usim
	public AdnRecordLoader(IccFileHandler fh) {
		// The telephony unit-test cases may create AdnRecords
		// in secondary threads
		super(Looper.getMainLooper());
		mFh = fh;
	}

	/**
	 * Resulting AdnRecord is placed in response.obj.result or
	 * response.obj.exception is set
	 */
	public void loadFromEF(int ef, int extensionEF, int recordNumber,
			Message response) {
		this.ef = ef;
		this.extensionEF = extensionEF;
		this.recordNumber = recordNumber;
		this.userResponse = response;

		/*
		 * phone.mIccFileHandler.loadEFLinearFixed( ef, recordNumber,
		 * obtainMessage(EVENT_ADN_LOAD_DONE));
		 */
		mFh.loadEFLinearFixed(ef, recordNumber,
				obtainMessage(EVENT_ADN_LOAD_DONE));
	}

	/**
	 * Resulting ArrayList&lt;adnRecord> is placed in response.obj.result or
	 * response.obj.exception is set
	 */
	public void loadAllFromEF(int ef, int extensionEF, Message response) {
		this.ef = ef;
		this.extensionEF = extensionEF;
		this.userResponse = response;

		/*
		 * phone.mIccFileHandler.loadEFLinearFixedAll( ef,
		 * obtainMessage(EVENT_ADN_LOAD_ALL_DONE));
		 */
		mFh.loadEFLinearFixedAll(ef, obtainMessage(EVENT_ADN_LOAD_ALL_DONE));
	}

	/**
	 * Write adn to a EF SIM record It will get the record size of EF record and
	 * compose hex adn array then write the hex array to EF record
	 * 
	 * @param adn
	 *            is set with alphaTag and phoneNubmer
	 * @param ef
	 *            EF fileid
	 * @param extensionEF
	 *            extension EF fileid
	 * @param recordNumber
	 *            1-based record index
	 * @param pin2
	 *            for CHV2 operations, must be null if pin2 is not needed
	 * @param response
	 *            will be sent to its handler when completed
	 */
	public void updateEF(AdnRecord adn, int ef, int extensionEF,
			int recordNumber, String pin2, Message response) {
		this.ef = ef;
		this.extensionEF = extensionEF;
		this.recordNumber = recordNumber;
		this.userResponse = response;
		this.pin2 = pin2;

		/*
		 * phone.mIccFileHandler.getEFLinearRecordSize( ef,
		 * obtainMessage(EVENT_EF_LINEAR_RECORD_SIZE_DONE, adn));
		 */
		mFh.getEFLinearRecordSize(ef, obtainMessage(
				EVENT_EF_LINEAR_RECORD_SIZE_DONE, adn));
	}

	// ***** Overridden from Handler
	// add multi record and email in usim begin
	public void updateEFAdnToUsim(AdnRecord adn, int ef, int extensionEF,
			int recordNumber, String pin2, Message response) {
		this.ef = ef;
		this.extensionEF = extensionEF;
		this.recordNumber = recordNumber;
		this.userResponse = response;
		this.pin2 = pin2;
		mFh.getEFLinearRecordSize(ef, obtainMessage(
				EVENT_EF_LINEAR_RECORD_SIZE_DONE, adn));
	}

	public void updateEFEmailToUsim(AdnRecord adn, int emailEF, int emailNum,
			int efid, int adnNum, int emailTagInIap, String pin2,
			Message response) {
		
		this.emailEF = emailEF;
		this.emailNum = emailNum;
		this.ef = efid;
		this.adnNum = adnNum;
		this.emailTagInIap = emailTagInIap;
		this.userResponse = response;
		this.pin2 = pin2;
		mFh.getEFLinearRecordSize(emailEF, obtainMessage(
				EVENT_EF_PBR_EMAIL_LINEAR_RECORD_SIZE_DONE, adn));

	}
       public void updateEFEmailToUsim(AdnRecord adn, ArrayList<Integer> emailEfids,
			 ArrayList<Integer> emailNums,int efid,int adnNum,ArrayList<Integer> emailTagInIaps, String pin2,Message response) {
             Log.i("AdnRecordLoader ","updateEFEmailToUsim  emailEfids " +emailEfids + "emailNums "+emailNums + "emailTagInIaps " + emailTagInIaps);
		this.emailEfids = emailEfids;
		this.emailNums = emailNums;
		this.ef = efid;
		this.adnNum = adnNum;
	
		this.emailTagInIaps = emailTagInIaps;
		this.userResponse = response;
		this.pin2 = pin2;
		mFh.getEFLinearRecordSize(emailEfids.get(0), obtainMessage(
				EVENT_EF_PBR_EMAIL_LINEAR_RECORD_SIZE_DONE, adn));

	}

	public void updateEFAnrToUsim(AdnRecord adn, ArrayList<Integer> anrefids,
			int efid, int adnNum, String pin2, Message response) {
		this.anrefids = anrefids;
		this.ef = efid;
		this.adnNum = adnNum;
		this.userResponse = response;
		this.pin2 = pin2;
		mFh.getEFLinearRecordSize(anrefids.get(0), obtainMessage(
				EVENT_EF_PBR_ANR_LINEAR_RECORD_SIZE_DONE, adn));

	}

	public void updateEFAnrToUsim(AdnRecord adn, ArrayList<Integer> anrefids,
			int efid, int adnNum, ArrayList<Integer> anrNums, String pin2, Message response) {
		this.anrefids = anrefids;
		this.ef = efid;
		this.adnNum = adnNum;
		this.userResponse = response;
		this.pin2 = pin2;
		this.anrNums = anrNums;
		mFh.getEFLinearRecordSize(anrefids.get(0), obtainMessage(
				EVENT_EF_PBR_ANR_LINEAR_RECORD_SIZE_DONE, adn));
	 }


	public void updateEFIapToUsim(AdnRecord adn, int iapEF, int adnNum,
			int emailNumInIap, String pin2, Message response) {
		this.iapEF = iapEF;
		this.adnNum = adnNum;
		this.emailNumInIap = emailNumInIap;
		this.userResponse = response;
		this.pin2 = pin2;
		mFh.getEFLinearRecordSize(iapEF, obtainMessage(
				EVENT_EF_PBR_IAP_LINEAR_RECORD_SIZE_DONE, adn));

	}

	public void updateEFIapToUsim(AdnRecord adn, int iapEF, int adnNum,
			byte[] record, String pin2, Message response) {
		 Log.i("AdnRecordLoader ","updateEFIapToUsim  iapEF " +iapEF );
		this.iapEF = iapEF;
		this.adnNum = adnNum;
		this.iapRec = record;
		this.userResponse = response;
		this.pin2 = pin2;
		mFh.getEFLinearRecordSize(iapEF, obtainMessage(
				EVENT_EF_PBR_IAP_LINEAR_RECORD_SIZE_DONE, adn));

	}

	// add multi record and email in usim end
	public void handleMessage(Message msg) {
		AsyncResult ar;
		byte data[];
		AdnRecord adn;

		try {
			switch (msg.what) {
			case EVENT_EF_LINEAR_RECORD_SIZE_DONE:
				ar = (AsyncResult) (msg.obj);
				adn = (AdnRecord) (ar.userObj);

				if (ar.exception != null) {
					throw new RuntimeException("get EF record size failed",
							ar.exception);
				}

				int[] recordSize = (int[]) ar.result;
				// recordSize is int[3] array
				// int[0] is the record length
				// int[1] is the total length of the EF file
				// int[2] is the number of records in the EF file
				// So int[0] * int[2] = int[1]
				if (recordSize.length != 3 || recordNumber > recordSize[2]) {
					throw new RuntimeException(
							"get wrong EF record size format", ar.exception);
				}

				data = adn.buildAdnString(recordSize[0]);

				if (data == null ) {
					throw new RuntimeException("worong ADN format",
							ar.exception);
				}
				Log.i("AdnRecordLoader", "recordNumber " + recordNumber);
				/*
				 * phone.mIccFileHandler.updateEFLinearFixed(ef, recordNumber,
				 * data, pin2, obtainMessage(EVENT_UPDATE_RECORD_DONE));
				 */
				mFh.updateEFLinearFixed(ef, recordNumber, data, pin2,
						obtainMessage(EVENT_UPDATE_RECORD_DONE));
				pendingExtLoads = 1;

				break;

			case EVENT_ADN_LOAD_DONE:
				ar = (AsyncResult) (msg.obj);
				data = (byte[]) (ar.result);

				if (ar.exception != null) {
					throw new RuntimeException("load failed", ar.exception);
				}

				if (false) {
					Log.d(LOG_TAG, "ADN EF: 0x" + Integer.toHexString(ef) + ":"
							+ recordNumber + "\n"
							+ IccUtils.bytesToHexString(data));
				}

				adn = new AdnRecord(ef, recordNumber, data);
				result = adn;

				if (adn.hasExtendedRecord()) {
					// If we have a valid value in the ext record field,
					// we're not done yet: we need to read the corresponding
					// ext record and append it

					pendingExtLoads = 1;

					/*
					 * phone.mIccFileHandler.loadEFLinearFixed(extensionEF,
					 * adn.extRecord, obtainMessage( EVENT_EXT_RECORD_LOAD_DONE,
					 * adn));
					 */
					mFh.loadEFLinearFixed(extensionEF, adn.extRecord,
							obtainMessage(EVENT_EXT_RECORD_LOAD_DONE, adn));
				}
				break;

			case EVENT_EXT_RECORD_LOAD_DONE:
				ar = (AsyncResult) (msg.obj);
				data = (byte[]) (ar.result);
				adn = (AdnRecord) (ar.userObj);

				if (ar.exception != null) {
					throw new RuntimeException("load failed", ar.exception);
				}

				Log.d(LOG_TAG, "ADN extention EF: 0x"
						+ Integer.toHexString(extensionEF) + ":"
						+ adn.extRecord + "\n"
						+ IccUtils.bytesToHexString(data));

				adn.appendExtRecord(data);

				pendingExtLoads--;
				// result should have been set in
				// EVENT_ADN_LOAD_DONE or EVENT_ADN_LOAD_ALL_DONE
				break;

			case EVENT_ADN_LOAD_ALL_DONE:
				ar = (AsyncResult) (msg.obj);
				ArrayList<byte[]> datas = (ArrayList<byte[]>) (ar.result);

				if (ar.exception != null) {
					throw new RuntimeException("load failed", ar.exception);
				}

				adns = new ArrayList<AdnRecord>(datas.size());
				result = adns;
				pendingExtLoads = 0;

				for (int i = 0, s = datas.size(); i < s; i++) {
					adn = new AdnRecord(ef, 1 + i, datas.get(i));
					adns.add(adn);

					if (adn.hasExtendedRecord()) {
						// If we have a valid value in the ext record field,
						// we're not done yet: we need to read the corresponding
						// ext record and append it

						pendingExtLoads++;

						/*
						 * phone.mIccFileHandler.loadEFLinearFixed(extensionEF,
						 * adn.extRecord, obtainMessage(
						 * EVENT_EXT_RECORD_LOAD_DONE, adn));
						 */
						mFh.loadEFLinearFixed(extensionEF, adn.extRecord,
								obtainMessage(EVENT_EXT_RECORD_LOAD_DONE, adn));
					}
				}
				break;
			// add multi record and email in usim begin
			case EVENT_EF_PBR_EMAIL_LINEAR_RECORD_SIZE_DONE:
				Log.d(LOG_TAG,
						"EVENT_EF_PBR_EMAIL_LINEAR_RECORD_SIZE_DONE emailNum :"
								+ emailNums);
				ar = (AsyncResult) (msg.obj);
				adn = (AdnRecord) (ar.userObj);

				if (ar.exception != null) {
					throw new RuntimeException("get EF record size failed",
							ar.exception);
				}
                      
				recordSize = (int[]) ar.result;
				// recordSize is int[3] array
				// int[0] is the record length
				// int[1] is the total length of the EF file
				// int[2] is the number of records in the EF file
				// So int[0] * int[2] = int[1]
       			 Log.d(LOG_TAG,
						"EVENT_EF_PBR_EMAIL_LINEAR_RECORD_SIZE_DONE (1) :  adnNum " + adnNum + "number " + 	recordSize[2]				);
				if (recordSize.length != 3/* || adnNum > recordSize[2]*/) {
					throw new RuntimeException(
							"get wrong EF record size format", ar.exception);
				}
				   Log.d(LOG_TAG,
						"EVENT_EF_PBR_EMAIL_LINEAR_RECORD_SIZE_DONE (2) :"
								);
				fileCount = 0;
				for (int i = 0, size = emailEfids.size(); i < size; i++) {
					Log.e("GSM", "efids.get(" + i + ") is " + emailEfids.get(i) + " number " + emailNums.get(i) );

					data = adn.buildEmailString(recordSize[0], i, ef, adnNum);
					
					if (data == null) {
						throw new RuntimeException("wrong ADN format", ar.exception);
					}
                                
					if (emailEfids.get(i) != 0 &&emailNums.get(i)!=0 ) {
						fileCount++;
						mFh.updateEFLinearFixed(emailEfids.get(i), emailNums.get(i), data,
								pin2,
								obtainMessage(EVENT_UPDATE_RECORD_DONE));
					}
				}

				pendingExtLoads = 1;

				break;

			case EVENT_EF_PBR_ANR_LINEAR_RECORD_SIZE_DONE:
				ar = (AsyncResult) (msg.obj);
				adn = (AdnRecord) (ar.userObj);
				Log.e("GSM", "EVENT_EF_PBR_ANR_LINEAR_RECORD_SIZE_DONE");

				if (ar.exception != null) {
					throw new RuntimeException("get EF record size failed",
							ar.exception);
				}
				recordSize = (int[]) ar.result;
				 Log.d(LOG_TAG,
						"EVENT_EF_PBR_ANR_LINEAR_RECORD_SIZE_DONE (1) :  adnNum " + adnNum + "number " + 	recordSize[2]				);
				if (recordSize.length != 3 /*|| adnNum > recordSize[2]*/) {
					throw new RuntimeException(
							"get wrong EF record size format", ar.exception);
				}
				Log.e("GSM", "anrefids = " + anrefids);
				Log.e("GSM", "anrefids.size is " + anrefids.size()
						+ "   ADNefid == ef:" + ef);

				fileCount = 0;
				for (int i = 0, size = anrefids.size(); i < size; i++) {
					Log.e("GSM", "efids.get(" + i + ") is " + anrefids.get(i) + " number " + anrNums.get(i));

					data = adn.buildAnrString(recordSize[0], i, ef, adnNum);

					if (anrefids.get(i)!=0 && anrNums.get(i)!=0) {
						fileCount++;
						mFh.updateEFLinearFixed(anrefids.get(i),
								anrNums.get(i), data, pin2,
								obtainMessage(EVENT_UPDATE_ANR_RECORD_DONE));
					}
				}
				pendingExtLoads = 1;
				break;

		
			
			// add end
			case EVENT_EF_PBR_IAP_LINEAR_RECORD_SIZE_DONE:
				ar = (AsyncResult) (msg.obj);
				adn = (AdnRecord) (ar.userObj);

				if (ar.exception != null) {
					throw new RuntimeException("get EF record size failed",
							ar.exception);
				}

				recordSize = (int[]) ar.result;
				// recordSize is int[3] array
				// int[0] is the record length
				// int[1] is the total length of the EF file
				// int[2] is the number of records in the EF file
				// So int[0] * int[2] = int[1]
				if (recordSize.length != 3 || adnNum > recordSize[2]) {
					throw new RuntimeException(
							"get wrong EF record size format", ar.exception);
				}
				if (this.iapRec != null) {

					data = this.iapRec;
				} else {

					data = adn.buildIapString(recordSize[0], 0xff);
				}
				if (data == null) {
					throw new RuntimeException("worong ADN format",
							ar.exception);
				}

				mFh.updateEFLinearFixed(iapEF, adnNum, data, pin2,
						obtainMessage(EVENT_UPDATE_RECORD_DONE));

				pendingExtLoads = 1;

				break;

			case EVENT_UPDATE_RECORD_DONE:
				Log.e("AdnRecordLoader", "EVENT_UPDATE_RECORD_DONE");
				ar = (AsyncResult) (msg.obj);
				if (ar.exception != null) {
					throw new RuntimeException("update EF adn record failed",
							ar.exception);
				}
				pendingExtLoads = 0;
				result = null;
				break;

			case EVENT_UPDATE_ANR_RECORD_DONE:
				ar = (AsyncResult) (msg.obj);
				if (ar.exception != null) {
					throw new RuntimeException("update EF adn record failed",
							ar.exception);
				}
				Log.e(LOG_TAG, "EVENT_UPDATE_ANR_RECORD_DONE, message is "
						+ msg.toString() + "fileCount " + fileCount);
				pendingExtLoads = 0;
				result = null;
				fileCount--;
				if (fileCount == 0)
					break;
				else
					return;
				// add multi record and email in usim end

			}
		} catch (RuntimeException exc) {
			if (userResponse != null) {
				AsyncResult.forMessage(userResponse).exception = exc;
				userResponse.sendToTarget();
				// Loading is all or nothing--either every load succeeds
				// or we fail the whole thing.
				userResponse = null;
			}
			return;
		}

		if (userResponse != null && pendingExtLoads == 0) {
			AsyncResult.forMessage(userResponse).result = result;

			userResponse.sendToTarget();
			userResponse = null;
		}
	}

}
