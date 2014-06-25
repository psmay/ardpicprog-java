package us.hfgk.ardpicprog;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import us.hfgk.ardpicprog.pylike.Po;
import us.hfgk.ardpicprog.pylike.PylikeReadable;
import us.hfgk.ardpicprog.pylike.PylikeWritable;
import us.hfgk.ardpicprog.pylike.Str;

public class Actions {
	private static final Logger log = Logger.getLogger(Actions.class.getName());

	static void doBurn(boolean forceCalibration, Programmer port, HexFile hexFile) throws IOException {
		port.write(hexFile, forceCalibration);
	}

	static void doCCOutput(Str ccOutput, boolean skipOnes, HexFile hexFile) throws IOException {
		PylikeWritable file = Po.openwb(ccOutput);
		HexFileSerializer.saveCC(hexFile, file, skipOnes);
		file.close();
	}

	static void doErase(boolean forceCalibration, Programmer port, HexFile hexFile) throws EraseException {
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

	static void doListDevices(Programmer port) throws IOException {
		log.info("Supported devices:\n" + port.devices() + "* = autodetected");
	}

	static void doOutput(Str output, boolean skipOnes, Programmer port, HexFileMetadata hexMeta)
			throws IOException, HexFileException {
		ReadableShortList words = port.readAreas(hexMeta.getAreas());
		HexFile hexFile = new HexFile(hexMeta, words);

		PylikeWritable file = null;
		try {
			try {
				file = Po.openwb(output);
			} catch (IOException e) {
				log.severe("Could not open " + output + ": " + e.getMessage());
				throw e;
			}

			HexFileSerializer.save(hexFile, file, skipOnes);
			file.close();
		} finally {
			Common.closeWarnOnError(file, log);
		}
	}

	static void doBlankCheck(Programmer port, HexFileMetadata metadata) throws IOException {
		log.info("Checking whether device is blank");
		if (port.blankCheckAll(metadata)) {
			log.info("Device appears to be blank");
		} else {
			log.info("Device appears to be NOT blank");
		}
	}

	static Programmer getProgrammerPort(String port, int speed) throws IOException {
		log.info("Initializing programmer on port " + port + " ...");
		ProgrammerCommPort pcom = new ProgrammerCommPort();
		pcom.open(port, speed);
		Programmer prog = new Programmer(pcom);
		return prog;
	}

	static void describeHexFileDevice(HexFileMetadata metadata) {
		log.info("Device " + metadata.getDevice().deviceName + ", program memory: " + metadata.programSizeWords()
				+ " words, data memory: " + metadata.dataSizeBytes() + " bytes.");
	}

	static HexFileMetadata getHexMeta(int format, Map<Str, Str> details) throws IOException {
		return new HexFileMetadata(new DeviceDetails(details), format);
	}

	static HexFile loadHexFile(HexFileMetadata metadata, Str filename) throws IOException {
		PylikeReadable file = null;
		try {
			file = Po.openrb(filename);
			return HexFileParser.load(metadata, file);
		} finally {
			Common.closeWarnOnError(file, log);
		}
	}

}
