package us.hfgk.ardpicprog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.logging.Logger;

public class Actions {
	private static final Logger log = Logger.getLogger(Actions.class.getName());

	static void doBurn(boolean forceCalibration, ProgrammerPort port, HexFile hexFile) throws IOException {
		hexFile.writeTo(port, forceCalibration);
	}

	static void doCCOutput(String ccOutput, boolean skipOnes, HexFile hexFile) throws IOException {
		OutputStream file = Common.openForWrite(ccOutput);
		hexFile.saveCC(file, skipOnes);
		file.close();
	}

	static void doErase(boolean forceCalibration, ProgrammerPort port, HexFile hexFile) throws EraseException {
		if (forceCalibration) {
			if (hexFile.canForceCalibration()) {
				log.info("Erasing and removing code protection.");
				try {
					port.commandErase(true);
				} catch (IOException e) {
					throw new EraseException("Erase nopreserve command failed", e);
				}
			} else {
				throw new EraseException("Calibration data missing from input");
			}
		} else {
			log.info("Erasing and removing code protection.");
			try {
				port.commandErase(false);
			} catch (IOException e) {
				throw new EraseException("Erase command failed", e);
			}
		}
	}

	static void doListDevices(ProgrammerPort port) throws IOException {
		log.info("Supported devices:\n" + port.devices() + "* = autodetected");
	}

	static void doOutput(String output, boolean skipOnes, ProgrammerPort port, HexFileMetadata hexMeta) throws IOException {
		ShortList words = Common.getBlankShortList();		
		HexFile.readFrom(words, port.getShortSource(), hexMeta.getAreas());		
		HexFile hexFile = new HexFile(hexMeta, words);

		OutputStream file = null;
		try {
			try {
				file = Common.openForWrite(output);
			} catch (IOException e) {
				log.severe("Could not open " + output + ": " + e.getMessage());
				throw e;
			}

			hexFile.save(file, skipOnes);
			file.close();
		} finally {
			Common.closeWarnOnError(file, log);
		}
	}

	static void doBlankCheck(ProgrammerPort port, HexFileMetadata metadata) throws IOException {
		log.info("Checking whether device is blank");
		if (HexFile.blankCheckRead(metadata, port.getShortSource())) {
			log.info("Device appears to be blank");
		} else {
			log.info("Device appears to be NOT blank");
		}
	}

	static ProgrammerPort getProgrammerPort(String port, int speed) throws IOException {
		log.info("Initializing programmer on port " + port + " ...");
		ProgrammerCommPort pcom = new RxTxProgrammerCommPort();
		pcom.open(port, speed);
		ProgrammerPort prog = new ProgrammerPort(pcom);
		return prog;
	}

	static void describeHexFileDevice(HexFileMetadata metadata) {
		log.info("Device " + metadata.getDevice().deviceName + ", program memory: " + metadata.programSizeWords()
				+ " words, data memory: " + metadata.dataSizeBytes() + " bytes.");
	}
	
	static HexFileMetadata getHexMeta(int format, Map<String, String> details) throws IOException {
		return new HexFileMetadata(new DeviceDetails(details), format);
	}
	
	static HexFile loadHexFile(HexFileMetadata metadata, String input) throws IOException {
		InputStream file = null;		
		try {
			file = Common.openForRead(input);
			return HexFileParser.load(metadata, file);
		}
		finally {
			Common.closeWarnOnError(file, log);
		}
	}



}
