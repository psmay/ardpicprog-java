package us.hfgk.ardpicprog;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import us.hfgk.ardpicprog.HexFile.HexFileException;
import us.hfgk.ardpicprog.ProgrammerPort.DeviceException;
import us.hfgk.ardpicprog.ProgrammerPort.ProgrammerException;

public class App {

	private static final Logger log = Logger.getLogger(App.class.getName());

	static class Options {
		boolean quiet = false;
		String device;
		String port;
		String input;
		String output;
		String ccOutput;
		int format = HexFile.FORMAT_AUTO;
		boolean skipOnes = false;
		boolean erase = false;
		boolean burn = false;
		boolean forceCalibration = false;
		boolean listDevices = false;
		boolean describeDevice = false;
		int speed = 9600;

		public Options() {
			String env;

			env = System.getenv("PIC_DEVICE");
			if (!Common.stringEmpty(env))
				device = env;

			env = System.getenv("PIC_PORT");
			port = Common.stringEmpty(env) ? RxTxProgrammerPort.getDefaultPicPort() : env;
		}

		public static final char BURN = 'b';
		public static final char CC_HEXFILE = 'c';
		public static final char COPYING = 'C';
		public static final char DEVICE = 'd';
		public static final char ERASE = 'e';
		public static final char FORCE_CALIBRATION = 'f';
		public static final char HELP = 'h';
		public static final char IGNORE = 'N';
		public static final char INPUT_HEXFILE = 'i';
		public static final char LIST_DEVICES = 'l';
		public static final char OUTPUT_HEXFILE = 'o';
		public static final char OUTPUT = 'o';
		public static final char PIC_SERIAL_PORT = 'p';
		public static final char PROGRAMMER_PORT = 'p';
		public static final char QUIET = 'q';
		public static final char SKIP_ONES = 's';
		public static final char SPEED = 'S';
		public static final char WARRANTY = 'w';
		public static final int DESCRIBE = 0x100001;
	}

	public static final String ARDPICPROG_VERSION = "0.1.2";

	private String programName = "ardpicprog";

	public App(String[] args) {
		Options options = new Options();

		// log.info(options.port);

		try {
			if (!parseCommandLineOptions(args, programName, options))
				return;

			// Print the header.
			if (!options.quiet)
				header();

			semanticOptionValidation(options);

			runWithOptions(options);
		} catch (UsageException e) {
			fatalExit(e, 64);
		} catch (HexFileException e) {
			fatalExit(e, 65);
		} catch (DeviceException e) {
			fatalExit(e, 76);
		} catch (ProgrammerException e) {
			fatalExit(e, 74);
		} catch (IOException e) {
			fatalExit(e, 66); // FIXME should be only on failure to open input
								// file or saveCC
		}
	}

	private void semanticOptionValidation(Options options) throws UsageException {
		// Bail out if we don't at least have -i, -o, --erase, or
		// --list-devices.
		if (Common.stringEmpty(options.input) && Common.stringEmpty(options.output) && !options.erase
				&& !options.listDevices && !options.describeDevice) {
			dieUsage("One of -i, -o, --erase, --list-devices, or --describe is required");
		}

		// Cannot use -c without -i.
		if (!Common.stringEmpty(options.ccOutput) && Common.stringEmpty(options.input)) {
			dieUsage("Cannot use --cc-hexfile without also specifying --input-hexfile");
		}

		// If we have -i, but no -c or --burn, then report an error.
		if (!Common.stringEmpty(options.input) && Common.stringEmpty(options.ccOutput) && !options.burn) {
			dieUsage("Cannot use --input-hexfile without also specifying --cc-hexfile or --burn");
		}

		// Cannot use --burn without -i.
		if (options.burn && Common.stringEmpty(options.input)) {
			dieUsage("Cannot use --burn without also specifying --input-hexfile");
		}

		// Will need --burn if doing --force-calibration.
		if (options.forceCalibration && !options.burn) {
			dieUsage("Cannot use --force-calibration without also specifying --burn");
		}
	}

	private void dieUsage(String message) throws UsageException {
		usage(programName);
		throw new UsageException(message);
	}

	static void header() {
		Common.notice("Ardpicprog version " + ARDPICPROG_VERSION + ", Copyright (c) 2012 Southern Storm Pty Ltd.",
				"Java port copyright (c) 2014 Peter S. May",
				"Ardpicprog comes with ABSOLUTELY NO WARRANTY; for details",
				"type `ardpicprog --warranty'.  This is free software,",
				"and you are welcome to redistribute it under certain conditions;",
				"type `ardpicprog --copying' for details.", "");
	}

	public static void main(String[] args) {
		new App(args);
	}

	private void fatalExit(Exception e, int exitCode) {
		String message = e.getMessage();
		if (message != null) {
			log.severe(programName + ": " + message);
		}
		log.log(Level.SEVERE, programName + ": ", e);
		System.exit(exitCode);
	}

	private boolean parseCommandLineOptions(String[] args, String programName, Options options) throws UsageException {

		Getopt g = new Getopt(programName, args, "c:d:hi:o:p:q", longOptions);

		int opt;
		while ((opt = g.getopt()) != -1) {
			switch (opt) {
			case HexFile.FORMAT_IHX8M:
			case HexFile.FORMAT_IHX16:
			case HexFile.FORMAT_IHX32:
				// Set the hexfile format: IHX8M, IHX16, or IHX32.
				options.format = opt;
				break;
			case Options.BURN:
				// Burn the PIC.
				options.burn = true;
				break;
			case Options.CC_HEXFILE:
				// Set the name of the cc output hexfile.
				options.ccOutput = g.getOptarg();
				break;
			case Options.COPYING:
				// Display copying message.
				Common.copying();
				return false;
			case Options.DESCRIBE:
				// Describe the device.
				options.describeDevice = true;
				break;
			case Options.DEVICE:
				// Set the type of PIC device to program.
				options.device = g.getOptarg();
				break;
			case Options.ERASE:
				// Erase the PIC.
				options.erase = true;
				options.describeDevice = true;
				break;
			case Options.FORCE_CALIBRATION:
				// Force reprogramming of the OSCCAL word from the hex file
				// rather than by automatic preservation.
				options.forceCalibration = true;
				break;
			case Options.INPUT_HEXFILE:
				// Set the name of the input hexfile.
				options.input = g.getOptarg();
				options.describeDevice = true;
				break;
			case Options.LIST_DEVICES:
				// List all devices that are supported by the programmer.
				options.listDevices = true;
				break;
			case Options.OUTPUT:
				// Set the name of the output hexfile.
				options.output = g.getOptarg();
				options.describeDevice = true;
				break;
			case Options.PROGRAMMER_PORT:
				// Set the serial port to use to access the programmer.
				options.port = g.getOptarg();
				break;
			case Options.QUIET:
				// Enable quiet mode.
				options.quiet = true;
				break;
			case Options.SKIP_ONES:
				// Skip memory locations that are all-ones when reading.
				options.skipOnes = true;
				break;
			case Options.SPEED:
				// Set the speed for the serial connection.
				options.speed = Common.parseInt(g.getOptarg());
				break;
			case Options.WARRANTY:
				// Display warranty message.
				Common.warranty();
				return false;
			case Options.IGNORE:
				// Option that is ignored for backwards compatibility.
				break;
			default:
				// Display the help message and exit.
				if (!options.quiet)
					header();

				dieUsage("Unrecognized command line option: " + opt + " (" + ((char) opt) + ")");
			}
		}

		return true;
	}

	private static final LongOpt[] longOptions = new LongOpt[] {
			new LongOpt("burn", LongOpt.NO_ARGUMENT, null, Options.BURN),
			new LongOpt("cc-hexfile", LongOpt.REQUIRED_ARGUMENT, null, Options.CC_HEXFILE),
			new LongOpt("copying", LongOpt.NO_ARGUMENT, null, Options.COPYING),
			new LongOpt("device", LongOpt.REQUIRED_ARGUMENT, null, Options.DEVICE),
			new LongOpt("describe", LongOpt.NO_ARGUMENT, null, Options.DESCRIBE),
			new LongOpt("erase", LongOpt.NO_ARGUMENT, null, Options.ERASE),
			new LongOpt("force-calibration", LongOpt.NO_ARGUMENT, null, Options.FORCE_CALIBRATION),
			new LongOpt("help", LongOpt.NO_ARGUMENT, null, Options.HELP),
			new LongOpt("ihx8m", LongOpt.NO_ARGUMENT, null, HexFile.FORMAT_IHX8M),
			new LongOpt("ihx16", LongOpt.NO_ARGUMENT, null, HexFile.FORMAT_IHX16),
			new LongOpt("ihx32", LongOpt.NO_ARGUMENT, null, HexFile.FORMAT_IHX32),
			new LongOpt("input-hexfile", LongOpt.REQUIRED_ARGUMENT, null, Options.INPUT_HEXFILE),
			new LongOpt("output-hexfile", LongOpt.REQUIRED_ARGUMENT, null, Options.OUTPUT_HEXFILE),
			new LongOpt("pic-serial-port", LongOpt.REQUIRED_ARGUMENT, null, Options.PIC_SERIAL_PORT),
			new LongOpt("quiet", LongOpt.NO_ARGUMENT, null, Options.QUIET),
			new LongOpt("skip-ones", LongOpt.NO_ARGUMENT, null, Options.SKIP_ONES),
			new LongOpt("warranty", LongOpt.NO_ARGUMENT, null, Options.WARRANTY),

			/*
			 * The following are ignored - backwards compatibility with picprog
			 */
			new LongOpt("jdm", LongOpt.NO_ARGUMENT, null, Options.IGNORE),
			new LongOpt("k8048", LongOpt.NO_ARGUMENT, null, Options.IGNORE),
			new LongOpt("nordtsc", LongOpt.NO_ARGUMENT, null, Options.IGNORE),
			new LongOpt("rdtsc", LongOpt.NO_ARGUMENT, null, Options.IGNORE),
			new LongOpt("reboot", LongOpt.NO_ARGUMENT, null, Options.IGNORE),
			new LongOpt("slow", LongOpt.NO_ARGUMENT, null, Options.IGNORE),

			/*
			 * These options are specific to ardpicprog - not present in picprog
			 */
			new LongOpt("list-devices", LongOpt.NO_ARGUMENT, null, Options.LIST_DEVICES),
			new LongOpt("speed", LongOpt.REQUIRED_ARGUMENT, null, Options.SPEED) };

	static void usage(String argv0) {
		Common.notice("Usage: " + argv0 + " --quiet -q --warranty --copying --help -h",
				"    --device DEVTYPE -d DEVTYPE --pic-serial-port PORT -p PORT",
				"    --input-hexfile INPUT -i INPUT --output-hexfile OUTPUT -o OUTPUT",
				"    --ihx8m --ihx16 --ihx32 --cc-hexfile CCFILE -c CCFILE --skip-ones",
				"    --erase --burn --force-calibration --list-devices --speed SPEED");
	}

	private void runWithOptions(Options options) throws IOException, FileNotFoundException {
		// Try to open the serial port and initialize the programmer.
		ProgrammerPort port = null;

		try {
			port = Actions.getProgrammerPort(options.port, options.speed);

			// Does the user want to list the available devices?
			if (options.listDevices) {
				Actions.doListDevices(port);
				return;
			}

			// Initialize the device.
			Map<String, String> details = port.initDevice(options.device);

			// Copy the device details into the hex file object.
			HexFile hexFile = Actions.getHexFile(options, details);

			// Dump the type of device and how much memory it has.
			if (options.describeDevice) {
				Actions.describeHexFileDevice(hexFile);
			}

			// Read the input file.
			if (!Common.stringEmpty(options.input)) {
				Actions.doInput(options, hexFile);
			}

			// Copy the input to the CC output file.
			if (!Common.stringEmpty(options.ccOutput)) {
				Actions.doCCOutput(options, hexFile);
			}

			// Erase the device if necessary. If --force-calibration is
			// specified
			// and we have an input that includes calibration information, then
			// use
			// the "NOPRESERVE" option when erasing.
			if (options.erase) {
				Actions.doErase(options, port, hexFile);
			}

			// Burn the input file into the device if requested.
			if (options.burn) {
				Actions.doBurn(options, port, hexFile);
			}

			// If we have an output file, then read the contents of the PIC into
			// it.
			if (!Common.stringEmpty(options.output)) {
				Actions.doOutput(options, port, hexFile);
			}
		} finally {
			if (port != null) {
				log.info("Closing programmer...");
				try {
					port.close();
				} catch (IOException e) {
					log.warning("Problem while closing programmer port: " + e.getMessage());
				}
			}
			log.info("Done");
		}
	}

	private static class UsageException extends Exception {
		private static final long serialVersionUID = 1L;

		public UsageException(String arg0) {
			super(arg0);
		}
	}
}
