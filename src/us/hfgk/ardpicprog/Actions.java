package us.hfgk.ardpicprog;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.Logger;

import us.hfgk.ardpicprog.ProgrammerPort.EraseException;

import us.hfgk.ardpicprog.HexFile.HexFileException;

public class Actions {
	private static final Logger log = Logger.getLogger(Actions.class.getName());

	static void doBurn(App.Options options, ProgrammerPort port, HexFile hexFile) throws IOException {
		hexFile.write(port, options.forceCalibration);
	}

	static void doCCOutput(App.Options options, HexFile hexFile) throws IOException {
		hexFile.saveCC(options.ccOutput, options.skipOnes);
	}

	static void doErase(App.Options options, ProgrammerPort port, HexFile hexFile) throws IOException {
		if (options.forceCalibration) {
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

	static void doInput(App.Options options, HexFile hexFile) throws FileNotFoundException, IOException {
		InputStream file = Common.openForRead(options.input);
		hexFile.load(file);
		file.close();
	}

	static void doListDevices(ProgrammerPort port) throws IOException {
		log.info("Supported devices:\n" + port.devices() + "* = autodetected");
	}

	static void doOutput(App.Options options, ProgrammerPort port, HexFile hexFile) throws IOException {
		hexFile.read(port);
		hexFile.save(options.output, options.skipOnes);
	}

	public static void doBlankCheck(App.Options options, ProgrammerPort port, HexFile hexFile) throws IOException {
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

	static HexFile getHexFile(App.Options options, Map<String, String> details) throws HexFileException {
		HexFile hexFile = new HexFile();
		hexFile.setDeviceDetails(details);
		hexFile.setFormat(options.format);
		return hexFile;
	}

}
