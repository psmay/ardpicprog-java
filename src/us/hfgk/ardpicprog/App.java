package us.hfgk.ardpicprog;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
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
		int speed = 9600;

		public Options() {
			String env;

			env = System.getenv("PIC_DEVICE");
			if (!Common.stringEmpty(env))
				device = env;

			env = System.getenv("PIC_PORT");
			port = Common.stringEmpty(env) ? RxTxProgrammerPort.getDefaultPicPort() : env;
		}
	}

	public static final String ARDPICPROG_VERSION = "0.1.2";

	private String programName = "ardpicprog";
	
	public App(String[] args) {
		Options options = new Options();

		log.info(options.port);

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
			fatalExit(e, 66); // FIXME should be only on failure to open input file or saveCC
		}
	}

	private void semanticOptionValidation(Options options) throws UsageException {
		// Bail out if we don't at least have -i, -o, --erase, or
		// --list-devices.
		if (Common.stringEmpty(options.input) && Common.stringEmpty(options.output) && !options.erase
				&& !options.listDevices) {
			dieUsage("One of -i, -o, --erase, or --list-devices is required");
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
		log.severe(programName + ": " + e.getMessage());
		System.exit(exitCode);
	}

	private boolean parseCommandLineOptions(String[] args, String programName, Options options)
			throws UsageException {

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
			case 'b':
				// Burn the PIC.
				options.burn = true;
				break;
			case 'c':
				// Set the name of the cc output hexfile.
				options.ccOutput = g.getOptarg();
				break;
			case 'C':
				// Display copying message.
				Common.copying();
				return false;
			case 'd':
				// Set the type of PIC device to program.
				options.device = g.getOptarg();
				break;
			case 'e':
				// Erase the PIC.
				options.erase = true;
				break;
			case 'f':
				// Force reprogramming of the OSCCAL word from the hex file
				// rather than by automatic preservation.
				options.forceCalibration = true;
				break;
			case 'i':
				// Set the name of the input hexfile.
				options.input = g.getOptarg();
				break;
			case 'l':
				// List all devices that are supported by the programmer.
				options.listDevices = true;
				break;
			case 'o':
				// Set the name of the output hexfile.
				options.output = g.getOptarg();
				break;
			case 'p':
				// Set the serial port to use to access the programmer.
				options.port = g.getOptarg();
				break;
			case 'q':
				// Enable quiet mode.
				options.quiet = true;
				break;
			case 's':
				// Skip memory locations that are all-ones when reading.
				options.skipOnes = true;
				break;
			case 'S':
				// Set the speed for the serial connection.
				options.speed = Common.parseInt(g.getOptarg());
				break;
			case 'w':
				// Display warranty message.
				Common.warranty();
				return false;
			case 'N':
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

	private static final LongOpt[] longOptions = new LongOpt[] { new LongOpt("burn", LongOpt.NO_ARGUMENT, null, 'b'),
			new LongOpt("cc-hexfile", LongOpt.REQUIRED_ARGUMENT, null, 'c'),
			new LongOpt("copying", LongOpt.NO_ARGUMENT, null, 'C'),
			new LongOpt("device", LongOpt.REQUIRED_ARGUMENT, null, 'd'),
			new LongOpt("erase", LongOpt.NO_ARGUMENT, null, 'e'),
			new LongOpt("force-calibration", LongOpt.NO_ARGUMENT, null, 'f'),
			new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h'),
			new LongOpt("ihx8m", LongOpt.NO_ARGUMENT, null, HexFile.FORMAT_IHX8M),
			new LongOpt("ihx16", LongOpt.NO_ARGUMENT, null, HexFile.FORMAT_IHX16),
			new LongOpt("ihx32", LongOpt.NO_ARGUMENT, null, HexFile.FORMAT_IHX32),
			new LongOpt("input-hexfile", LongOpt.REQUIRED_ARGUMENT, null, 'i'),
			new LongOpt("output-hexfile", LongOpt.REQUIRED_ARGUMENT, null, 'o'),
			new LongOpt("pic-serial-port", LongOpt.REQUIRED_ARGUMENT, null, 'p'),
			new LongOpt("quiet", LongOpt.NO_ARGUMENT, null, 'q'),
			new LongOpt("skip-ones", LongOpt.NO_ARGUMENT, null, 's'),
			new LongOpt("warranty", LongOpt.NO_ARGUMENT, null, 'w'),

			/* The following are ignored - backwards compatibility with picprog */
			new LongOpt("jdm", LongOpt.NO_ARGUMENT, null, 'N'), new LongOpt("k8048", LongOpt.NO_ARGUMENT, null, 'N'),
			new LongOpt("nordtsc", LongOpt.NO_ARGUMENT, null, 'N'),
			new LongOpt("rdtsc", LongOpt.NO_ARGUMENT, null, 'N'),
			new LongOpt("reboot", LongOpt.NO_ARGUMENT, null, 'N'), new LongOpt("slow", LongOpt.NO_ARGUMENT, null, 'N'),

			/* These options are specific to ardpicprog - not present in picprog */
			new LongOpt("list-devices", LongOpt.NO_ARGUMENT, null, 'l'),
			new LongOpt("speed", LongOpt.REQUIRED_ARGUMENT, null, 'S') };

	static void usage(String argv0) {
		Common.notice("Usage: " + argv0 + " --quiet -q --warranty --copying --help -h",
				"    --device DEVTYPE -d DEVTYPE --pic-serial-port PORT -p PORT",
				"    --input-hexfile INPUT -i INPUT --output-hexfile OUTPUT -o OUTPUT",
				"    --ihx8m --ihx16 --ihx32 --cc-hexfile CCFILE -c CCFILE --skip-ones",
				"    --erase --burn --force-calibration --list-devices --speed SPEED");
	}

	private void runWithOptions(Options options) throws IOException, FileNotFoundException {
		// Try to open the serial port and initialize the programmer.
		ProgrammerPort port = Actions.getProgrammerPort(options.port, options.speed);

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
		Actions.describeHexFileDevice(hexFile);

		// Read the input file.
		if (!Common.stringEmpty(options.input)) {
			Actions.doInput(options, hexFile);
		}

		// Copy the input to the CC output file.
		if (!Common.stringEmpty(options.ccOutput)) {
			Actions.doCCOutput(options, hexFile);
		}

		// Erase the device if necessary. If --force-calibration is specified
		// and we have an input that includes calibration information, then use
		// the "NOPRESERVE" option when erasing.
		if (options.erase) {
			Actions.doErase(options, port, hexFile);
		}

		// Burn the input file into the device if requested.
		if (options.burn) {
			Actions.doBurn(options, port, hexFile);
		}

		// If we have an output file, then read the contents of the PIC into it.
		if (!Common.stringEmpty(options.output)) {
			Actions.doOutput(options, port, hexFile);
		}
	}

	private static class UsageException extends Exception {
		private static final long serialVersionUID = 1L;

		public UsageException(String arg0) {
			super(arg0);
		}
	}
}
