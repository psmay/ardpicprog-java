ardpicprog-java
===============

This is a port of the host program from [rweather's
ardpicprog](https://github.com/rweather/ardpicprog) to Java.

Some refactoring has taken place with the objective of clarifying the
code. It may be too early to tell if this effort is successful. Most of
the guts haven't been extensively touched, other than to make the
original C++ look enough like Java to compile.

The code has not been thoroughly tested.

Dependencies
------------

*   RXTX
*   Java port of GNU Getopt

Running
-------

After this program is compiled, something like this should work:

    java -cp <classpath> us.hfgk.ardpicprog.App -p <port> --list-devices

Be sure to set `-p` to a value that makes sense (e.g. `-p COM1` on
Windows or `-p /dev/ttyUSB0` on Ubuntu); the default isn't too sensible
(yet).

Hardware
--------

Being a more-or-less straight port of the `ardpicprog` host, this
program is intended to be compatible with the same hardware—an Arduino
connected via [a minimal interface
circuit](http://rweather.github.io/ardpicprog/pic14_zif_circuit.html) to
a PIC microcontroller and loaded with either the
[ProgramPIC](http://rweather.github.io/ardpicprog/programpic_sketch.html)
sketch or the
[ProgramEEPROM](http://rweather.github.io/ardpicprog/programeeprom_sketch.html)
sketch.

Bugs
----

If you have access to both this software and the original `ardpicprog`
host, I'd like to hear how my version stacks up. If you think you've
found a bug, please [post an issue on
GitHub](https://github.com/psmay/ardpicprog-java/issues/new).

License
-------

Original code Copyright (C) 2012 Southern Storm Software, Pty Ltd.

Java port Copyright (C) 2014 Peter S. May

This program is free software: you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the
Free Software Foundation, either version 3 of the License, or (at your
option) any later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
Public License for more details.

You should have received a copy of the GNU General Public License along
with this program. If not, see <http://www.gnu.org/licenses/>.
