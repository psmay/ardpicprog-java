package us.hfgk.ardpicprog;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import us.hfgk.ardpicprog.HexFile.HexFileException;
import us.hfgk.ardpicprog.ProgrammerPort.EraseException;

public class Actions {
	private static final Logger log = Logger.getLogger(Actions.class.getName());

	static void doBurn(boolean forceCalibration, ProgrammerPort port, HexFile hexFile) throws IOException {
		hexFile.write(port, forceCalibration);
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

	static void doInput(String input, HexFile hexFile) throws FileNotFoundException, IOException {
		InputStream file = Common.openForRead(input);
		hexFile.load(file);
		file.close();
	}

	static void doListDevices(ProgrammerPort port) throws IOException {
		log.info("Supported devices:\n" + port.devices() + "* = autodetected");
	}

	static void doOutput(String output, boolean skipOnes, ProgrammerPort port, HexFile hexFile) throws IOException {
		hexFile.read(port);

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
			if (file != null) {
				try {
					file.close();
				} catch (IOException e) {
					log.log(Level.SEVERE, "Could not close stream", e);
				}
			}
		}
	}

	static void doBlankCheck(ProgrammerPort port, HexFile hexFile) throws IOException {
		log.info("Checking whether device is blank");
		if (hexFile.blankCheckRead(port)) {
			log.info("Device appears to be blank");
		} else {
			log.info("Device appears to be NOT blank");
		}
	}

	static ProgrammerPort getProgrammerPort(String port, int speed) throws IOException {
		log.info("Initializing programmer ...");
		ProgrammerCommPort sp = new RxTxProgrammerCommPort();
		sp.open(port, speed);
		ProgrammerPort pp = new ProgrammerPort(sp);
		return pp;
	}

	static void describeHexFileDevice(HexFile hexFile) {
		log.info("Device " + hexFile.deviceName() + ", program memory: " + hexFile.programSizeWords()
				+ " words, data memory: " + hexFile.dataSizeBytes() + " bytes.");
	}

	static HexFile getHexFile(int format, Map<String, String> details) throws HexFileException {
		HexFile hexFile = new HexFile();
		hexFile.setDeviceDetails(details);
		hexFile.setFormat(format);
		return hexFile;
	}

}
