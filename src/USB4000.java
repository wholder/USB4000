import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import static javax.swing.JOptionPane.showMessageDialog;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER;

/*
 *  Test Program to communicate with and control an Ocean Optics USB4000
 *
 *  Author: Wayne Holder, 2019
 *  License: MIT (https://opensource.org/licenses/MIT)
 */

  /*
    Bus: 000 Device 011: Vendor 0x2457, Product 0x1022
      interface: 0
        BLK add: 0x01 (OUT) pkt: 512    // Commands (output)
        BLK add: 0x82 (IN)  pkt: 512    // Spectral Data In
        BLK add: 0x86 (IN)  pkt: 512    // Spectral Data In
        BLK add: 0x81 (IN)  pkt: 512    // Command responses

      Gratings: https://oceanoptics.com/product-details/qe-pro-custom-configured-gratings-and-wavelength-range/

      H2 Grating: 250-800 nm (UV-VIS), 600 lines/mm

      Slits:      5, 10, 25, 50, 100, or 200 μm

      Colors in nanometers (nm)
        Red:    625–740 nm
        Orange: 590–625 nm
        Yellow: 565–590 nm
        Green:  500–565 nm
        Cyan:   485–500 nm
        Blue:   450–485 nm
        Violet: 380–450 nm
   */

public class USB4000 extends JFrame {
  private transient Preferences prefs = Preferences.userRoot().node(this.getClass().getName());
  private static final short    vendId = 0x2457;
  private static final short    prodId = 0x1022;
  private static final byte     iFace  = 0x00;
  private static final byte     eOut1 = 0x01;
  private static final byte     eIn1 = (byte) 0x81;
  private static final byte     eIn2 = (byte) 0x82;
  private static final byte     eIn6 = (byte) 0x86;
  private static final byte[]   init = new byte[] {(byte) 0x01};
  private static final byte[]   queryStatus = new byte[] {(byte) 0xFE};

  enum state {SCAN, INFO, STOP}

  static class Spectrum extends JPanel implements Runnable {
    private static int        usableStart = 22;
    private static int        usableEnd = 3670;
    private static int        xAxisSize = 60;
    private int[]             spectrum;
    private int               xScale = 2;
    private int               yScale = 32;
    private int               mseX;
    private boolean           tracking;
    private double            coff0, coff1, coff2, coff3;
    private boolean           calLoaded;
    private List<Point>       xAxis;
    private List<RunState>    listeners = new ArrayList<>();
    private transient boolean running;
    private transient state   runState = state.SCAN;
    private transient int     scanRate;

    interface RunState {
      void isRunning (boolean running);
    }

    Spectrum () {
      setPreferredSize(new Dimension((usableEnd - usableStart) / 2, 512 + xAxisSize));
      addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseMoved (MouseEvent ev) {
          mseX = ev.getX();
          if (tracking) {
            repaint();
          }
        }
      });
      addMouseListener(new MouseListener() {
        @Override
        public void mouseClicked (MouseEvent e) { }

        @Override
        public void mousePressed (MouseEvent e) { }

        @Override
        public void mouseReleased (MouseEvent e) { }

        @Override
        public void mouseEntered (MouseEvent e) {
          tracking = true;
        }

        @Override
        public void mouseExited (MouseEvent e) {
          if (tracking) {
            repaint();
          }
          tracking = false;
        }
      });
    }

    @Override
    public void paint (Graphics g) {
      Dimension dim = getSize();
      Graphics2D g2 = (Graphics2D) g;
      g2.setColor(Color.white);
      g2.fillRect(0, 0, dim.width, 512);
      g2.setColor(new Color(230, 230, 230));
      g2.fillRect(0, 512, dim.width, dim.height);
      g2.setColor(Color.black);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      if (spectrum != null && spectrum.length > 0) {
        if (xScale == 1) {
          for (int ii = 0; ii < usableEnd - usableStart; ii++) {
            Point p1 = new Point(ii, 512 - spectrum[ii + usableStart] / yScale);
            Point p2 = new Point(ii + 1, 512 - spectrum[ii + usableStart + 1] / yScale);
            g2.drawLine(p1.x, p1.y, p2.x, p2.y);
          }
        } else {
          for (int ii = 0; ii < usableEnd - usableStart; ii += 2) {
            int avg1 = (int) ((long) spectrum[ii + usableStart] + (long) spectrum[ii + usableStart + 1]) / 2;
            int avg2 = (int) ((long) spectrum[ii + usableStart + 2] + (long) spectrum[ii + usableStart + 3]) / 2;
            Point p1 = new Point(ii / 2, 512 - avg1 / yScale);
            Point p2 = new Point(ii / 2 + 1, 512 - avg2 / yScale);
            g2.drawLine(p1.x, p1.y, p2.x, p2.y);
          }
        }
        if (tracking) {
          g2.setColor(Color.blue);
          g2.drawLine(mseX, 0, mseX, dim.height);
          int pixel = mseX  * xScale + usableStart;
          double nanometers = pixelToNanometers(pixel);
          String val = String.format("%3.1f", nanometers) + " nm";
          if (mseX < dim.width - 70) {
            g2.drawString(val, mseX + 10, 20);
            g2.drawString("" + pixel + " = " + spectrum[pixel], mseX + 10, 40);
          } else {
            g2.drawString(val, mseX - 70, 20);
            g2.drawString("" + pixel, mseX - 70, 40);
          }
          // Draw color swatch for color computed from nanometer value
          g2.setColor(WavelengthToRGB.getRBG(nanometers));
          g2.fillRect(mseX + 20, 50, 20, 20);
        }
        if (xAxis != null) {
          // Draw X Axis legend
          g2.setColor(Color.black);
          for (Point val : xAxis) {
            int x = val.x / xScale;
            g2.drawLine(x, 512 + 5, x, 512 + 15);
            g2.drawString(val.y + " nm", x - 20, 512 + 30);
          }
        }
      }
    }

    double pixelToNanometers (int px) {
      return coff0 + coff1 * px + coff2 * (px * px) + coff3 * (px * px * px);
    }

    void addRunStateListener (RunState listener) {
      listeners.add(listener);
    }

    public void run () {
      // Note: run time for a scan < 20 ms
      if (!running) {
        running = true;
        for (RunState listener : listeners) {
          listener.isRunning(running);
        }
        USBIO usb = new USBIO(vendId, prodId, iFace, eOut1, eIn1);
        byte[] data;
        usb.send(init);
        if (!calLoaded) {
          coff0 = Double.parseDouble(getInfo(usb, 1));
          coff1 = Double.parseDouble(getInfo(usb, 2));
          coff2 = Double.parseDouble(getInfo(usb, 3));
          coff3 = Double.parseDouble(getInfo(usb, 4));
          // Compute values for X Axis legend
          xAxis = new ArrayList<>();
          double base = pixelToNanometers(usableStart);
          int mult = 25;
          int next = mult * (((int) base - 1) / mult + 1);
          for (int ii = 0; ii < usableEnd - usableStart; ii++) {
            int px = ii + usableStart;
            double val = pixelToNanometers(px);
            if (val > next) {
              xAxis.add(new Point(ii, next));
              next += mult;
            }
          }
          calLoaded = true;
        }
        usb.send(queryStatus);
        data = usb.receive();
        boolean hsUsb = data[14] == (byte) 0x80;
        int pixels = (data[0] & 0xFF) + ((data[1] & 0xFF) << 8);
        switch (runState) {
        case SCAN:
          do {
            long scanStart = System.currentTimeMillis();
            List<byte[]> list = new ArrayList<>();
            int dataLength = 0;
            if (hsUsb) {
              // Read spectrum data via High Speed USB (480Mbps)
              usb.send(new byte[] {0x09});
              for (int ii = 0; ii < 4; ii++) {
                data = usb.receive(eIn6, 512);
                list.add(data);
                dataLength += data.length;
              }
              for (int ii = 0; ii < 12; ii++) {
                data = usb.receive(eIn2, 512);
                list.add(data);
                dataLength += data.length;
              }
            } else {
              // Read spectrum data via Full Speed USB (12Mbps) Note: Untested
              for (int ii = 0; ii < 121; ii++) {
                data = usb.receive(eIn2, 64);
                list.add(data);
                dataLength += data.length;
              }
            }
            spectrum = new int[dataLength / 2];
            int idx = 0;
            for (byte[] seg : list) {
              if (seg.length != 1) {
                for (int ii = 0; ii < seg.length; ii += 2) {
                  spectrum[idx++] = (seg[ii] & 0xFF) + ((seg[ii + 1] & 0xFF) << 8);
                }
              }
            }
            repaint();
            if (scanRate > 0) {
              long time = System.currentTimeMillis() - scanStart;
              long delay = 1000 / scanRate;
              try {
                Thread.sleep(delay - time);
              } catch (InterruptedException ex) {
                ex.printStackTrace();
              }
            }
          } while (scanRate > 0 && runState == state.SCAN);
          break;
        case INFO:
          InfoPane infoPane = new InfoPane();
          infoPane.addItem("Serial Num:", getInfo(usb, 0));
          String bench = getInfo(usb, 15);
          String[] parts = bench.split(" ");
          infoPane.addItem("Grating:", parts[0]);
          infoPane.addItem("Filter:", parts[1]);
          infoPane.addItem("Slit size:", parts[2] + " \u00B5m");
          infoPane.addItem("Pixel Count:", "" + pixels);
          infoPane.addItem("", "");
          infoPane.addItem("USB4000 cfg:", getInfo(usb, 16));
          infoPane.addItem("USB Speed:", hsUsb ? "480 Mbps" : "12 Mbs");
          infoPane.addItem("PCB Temp:", getPcbTemp(usb));
          infoPane.addItem("", "");
          infoPane.addItem("Cal Coff 0:", "" + coff0);
          infoPane.addItem("Cal Coff 1:", "" + coff1);
          infoPane.addItem("Cal Coff 2:", "" + coff2);
          infoPane.addItem("Cal Coff 3:", "" + coff3);
          showMessageDialog(this.getParent(), infoPane, "USB4000 Info", JOptionPane.PLAIN_MESSAGE, null);
          break;
        }
        usb.close();
        running = false;
        for (RunState listener : listeners) {
          listener.isRunning(running);
        }
      }
    }

    String getCsvData () {
      StringBuilder buf = new StringBuilder();
      for (int ii = usableStart; ii < usableEnd; ii++) {
        buf.append(ii - usableStart);
        buf.append(',');
        buf.append(spectrum[ii]);
        buf.append("\n");
      }
      return buf.toString();
    }

    static class InfoPane extends JPanel {

      InfoPane () {
        super(new GridLayout(0, 2));
      }

      void addItem (String label, String value) {
        GridLayout layout = (GridLayout) getLayout();
        layout.setRows(layout.getRows() + 1);
        JLabel lbl = new JLabel(label);
        add(lbl);
        lbl.setHorizontalAlignment(SwingConstants.LEFT);
        if (value.length() == 0) {
          add(new JLabel(""));
        } else {
          JTextField val = new JTextField(value);
          add(val);
          val.setHorizontalAlignment(SwingConstants.LEFT);
          val.setEditable(false);
          val.setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 3));
        }
      }
    }

    private static String getPcbTemp (USBIO usb) {
      usb.send(new byte[] {(byte) 0x6C});
      byte[] data = usb.receive();
      return String.format("%2.2f", ((data[1] & 0xFF) + ((data[1 + 1] & 0xFF) << 8)) * .003906) + "° C";
    }

    private static String getInfo (USBIO usb, int index) {
      byte[] cmd = new byte[] {0x05, (byte) index};
      usb.send(cmd);
      byte[] data = usb.receive();
      return getString(data);
    }

    private static String getString (byte[] data) {
      StringBuilder buf = new StringBuilder();
      for (int ii = 2; ii < data.length; ii++) {
        if (data[ii] == 0) {
          break;
        }
        buf.append((char) data[ii]);
      }
      return buf.toString();
    }

    void doScan (state action) {
      runState = action;
      new Thread(this).start();
    }

    void stopScan () {
      runState = state.STOP;
    }

    void setXScale (int xScale) {
      setPreferredSize(new Dimension((usableEnd - usableStart) / xScale, 512 + xAxisSize));
      this.xScale = xScale;
      revalidate();
      repaint();
    }

    void setYScale (int yScale) {
      this.yScale = yScale;
      repaint();
    }

    void setRate (int scanRate) {
      this.scanRate = scanRate;
    }

    boolean isRunning () {
      return running;
    }

    boolean hasScan () {
      return spectrum != null;
    }
  }

  private static class ComboMenu extends JMenu {
    private int   value;

    ComboMenu (String prefix, int[] values, String[] labels,  int currentValue) {
      if (values.length != labels.length) {
        throw new IllegalArgumentException("values.length != labels.length");
      }
      for (int ii =  0; ii < values.length; ii++) {
        if (currentValue == values[ii]) {
          setText(prefix + labels[ii]);
        }
      }
      this.value = currentValue;
      for (int ii = 0; ii < values.length; ii++) {
        int nval = value =  values[ii];
        String label = labels[ii];
        JMenuItem item = new JMenuItem(label);
        add(item);
        item.addActionListener(ev -> {
          this.value = nval;
          setText(prefix + label);
          this.fireActionPerformed(ev);
        });
      }
    }

    int getValue () {
      return value;
    }
  }

  private USB4000 () {
    setTitle("USB4000 Spectrum Viewer");
    setLayout(new BorderLayout());
    Spectrum spectrum = new Spectrum();
    JScrollPane scroll = new JScrollPane(spectrum);
    scroll.setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_NEVER);
    add(scroll, BorderLayout.CENTER);
    // Add MenuBar
    JMenuBar menuBar = new JMenuBar();
    // Add "Info" menu
    JMenu fileMenu = new JMenu("File");
    menuBar.add(fileMenu);
    JMenuItem getInfo = new JMenuItem("Get Info");
    fileMenu.add(getInfo);
    getInfo.addActionListener(ev -> spectrum.doScan(state.INFO));
    fileMenu.addSeparator();
    // Add "Save Data" menu item
    JMenuItem save = new JMenuItem("Save Scan");
    save.setEnabled(false);
    save.addActionListener(e -> {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle("Save Scan to CSV File");
      FileNameExtensionFilter nameFilter = new FileNameExtensionFilter("CSV files (*.csv)", "csv");
      fileChooser.addChoosableFileFilter(nameFilter);
      fileChooser.setFileFilter(nameFilter);
      fileChooser.setSelectedFile(new File(prefs.get("default.dir", "spectrum.csv")));
      if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
        File file = fileChooser.getSelectedFile();
        if (!file.exists() || JOptionPane
            .showConfirmDialog(this, "Overwrite Existing file?", "Warning", JOptionPane.YES_NO_OPTION,
                               JOptionPane.WARNING_MESSAGE, null) == JOptionPane.OK_OPTION) {
          try {
            FileOutputStream out = new FileOutputStream(file);
            out.write(spectrum.getCsvData().getBytes(StandardCharsets.UTF_8));
            out.close();
          } catch (IOException ex) {
            ex.printStackTrace();
          }
        }
        prefs.put("default.dir", file.getAbsolutePath());
      }
    });
    fileMenu.add(save);
    // Add "Scan" button
    JButton scan = new JButton("Scan");
    menuBar.add(scan);
    scan.addActionListener(ev -> {
      if (spectrum.isRunning()) {
        spectrum.stopScan();
      } else {
        spectrum.doScan(state.SCAN);
      }
    });
    // Add RunState Listener to update GUI state
    spectrum.addRunStateListener(running -> {
      scan.setText(running ? "Stop" : "Scan");
      fileMenu.setEnabled(!running);
      if (!running && spectrum.hasScan()) {
        save.setEnabled(true);
      }
    });
    // Add "Rate" menu
    int currentRate = prefs.getInt("scale.rate", 0);
    spectrum.setRate(currentRate);
    ComboMenu rate = new ComboMenu("Rate: ", new int[]{0, 1, 5, 10},
                                   new String[]{"Once ", "1 Hz ", "5 Hz ", "10 Hz"}, currentRate);
    rate.addActionListener(ev -> {
      int value = rate.getValue();
      spectrum.setRate(value);
      prefs.putInt("scale.rate", value);
    });
    menuBar.add(rate);
    // Add X Scale Menu
    int currentXScale = prefs.getInt("scale.x", 2);
    ComboMenu xScale = new ComboMenu("X: ", new int[]{1, 2},
                                     new String[]{"1/1", "1/2"}, currentXScale);
    xScale.addActionListener(ev -> {
      int value = xScale.getValue();
      spectrum.setXScale(value);
      prefs.putInt("scale.x", value);
    });
    menuBar.add(xScale);
    // Add Y Scale Menu
    int currentYScale = prefs.getInt("scale.y", 16);
    ComboMenu yScale = new ComboMenu("Y: ", new int[] {1, 2, 4, 8, 16, 32, 64},
                                     new String[]{"1/1", "1/2", "1/4", "1/8", "1/16", "1/32", "1/64"}, currentYScale);
    yScale.addActionListener(ev -> {
      int value = yScale.getValue();
      spectrum.setYScale(value);
      prefs.putInt("scale.y", value);
    });
    menuBar.add(yScale);
    setJMenuBar(menuBar);
    pack();
    spectrum.setXScale(currentXScale);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setResizable(false);
    setLocation(prefs.getInt("window.x", 10), prefs.getInt("window.y", 10));
    // Track window resize/move events and save in prefs
    addComponentListener(new ComponentAdapter() {
      public void componentMoved (ComponentEvent ev)  {
        Rectangle bounds = ev.getComponent().getBounds();
        prefs.putInt("window.x", bounds.x);
        prefs.putInt("window.y", bounds.y);
      }
    });
    setVisible(true);
  }

  public static void main (String[] args) {
    new USB4000();
  }
}
