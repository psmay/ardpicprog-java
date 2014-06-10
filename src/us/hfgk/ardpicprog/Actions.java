package us.hfgk.ardpicprog;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.Logger;

import us.hfgk.ardpicprog.HexFile.HexFileException;

public class Actions {
	private static final Logger log = Logger.getLogger(Actions.class.getName());
	
	static void doBurn(App.Options options, ProgrammerPort port, HexFile hexFile)
			throws IOException {
		hexFile.write(port, options.forceCalibration);
	}

	static void doCCOutput(App.Options options, HexFile hexFile)
			throws IOException {
		hexFile.saveCC(options.ccOutput, options.skipOnes);
	}

	static void doErase(App.Options options, ProgrammerPort port,
			HexFile hexFile) throws IOException {
		if (options.forceCalibration) {
			if (hexFile.canForceCalibration()) {
				log.info("Erasing and removing code protection.");
				try {
					port.command("ERASE NOPRESERVE");
				} catch (IOException e) {
					throw new IOException("Erase failed", e);
				}
			} else {
				throw new IOException("Calibration data missing from input");
			}
		} else {
			log.info("Erasing and removing code protection.");
			try {
				port.command("ERASE");
			} catch (IOException e) {
				throw new IOException("Erase failed");
			}
		}
	}

	static void doInput(App.Options options, HexFile hexFile)
			throws FileNotFoundException, IOException {
		InputStream file = Common.openForRead(options.input);
		hexFile.load(file);
		file.close();
	}

	static void doListDevices(ProgrammerPort port) throws IOException {
		log.info("Supported devices:");
		log.info(port.devices());
		log.info("* = autodetected");
	}

	static void doOutput(App.Options options, ProgrammerPort port,
			HexFile hexFile) throws IOException {
		hexFile.read(port);
		hexFile.save(options.output, options.skipOnes);
	}

	static ProgrammerPort getProgrammerPort(String port, int speed) throws IOException {
		log.info("Initializing programmer ...");
		ProgrammerPort sp = new RxTxProgrammerPort();
		sp.open(port, speed);
		return sp;
	}

	static void describeHexFileDevice(HexFile hexFile) {
		log.info("Device " + hexFile.deviceName() + ", program memory: "
		+ hexFile.programSizeWords() + " words, data memory: "
		+ hexFile.dataSizeBytes() + " bytes.");
	}

	static HexFile getHexFile(App.Options options,
			Map<String, String> details) throws HexFileException {
		HexFile hexFile = new HexFile();
		hexFile.setDeviceDetails(details);
		hexFile.setFormat(options.format);
		return hexFile;
	}

}
