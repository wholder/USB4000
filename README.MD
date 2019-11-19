<p align="center"><img src="https://github.com/wholder/USB4000/blob/master/images/USB4000%20Screenshot.png" width="100%" height="100%"></p>

This program is designed to read and display optical spectrum data from a USB4000 Spectrometer made by Ocean Optics, Inc.  This program is still in develpment, so only a few, basic features are currently supported:

  - Single scan, or continuous scan (1 Hz, 5 Hz, and 10 Hz)
  - Uses USB400's internally stored calibration coefficients to comupte pixel to nanometer mapping
  - Uses calibration coefficients to compute and display x axis legend
  - Mouse cursor moves a x-axis cursor that displays pixel level readings
  - Save scan as CSV file (pixel number and reading)
  - Get Info pane displays information about the USB400, including calibration coefficients
  - X Axis can be scaled for 1:1 (scrollable display of all pixels) or 1:2 (1/2 scale, nonscrolling display)
  - Y Axis can be scaled for 1:1 - 1:64 scales

### **Requirements**
A [Java JDK or JVM](https://www.java.com/en/) or [OpenJDK](http://openjdk.java.net) version 8, or later must be installed in order to run the code.  There is also a [**Runnable JAR file**](https://github.com/wholder/USB4000/blob/master/out/artifacts/USB4000_jar) included in the checked in code that you can download and run without having to compile the source code.

## Credits
USB4000 uses the following Java code to perform some of its functions, or build this project:
- [Usb4Java](http://usb4java.org) is used to perform the low-level USB I/O that talks to the USB4000.
- [IntelliJ IDEA from JetBrains](https://www.jetbrains.com/idea/) (my favorite development environment for Java coding. Thanks JetBrains!)
