package us.hfgk.ardpicprog.pylike;

public class Re {
	private Re() {
	}

	public static Str sub(Str pattern, Str repl, Str string) {
		return Str.val(string.toString().replaceAll(pattern.toString(), repl.toString()));
	}

}
