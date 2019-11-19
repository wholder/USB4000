import java.awt.*;

  // Adapted from https://gist.github.com/friendly/67a7df339aa999e2bcfcfec88311abfc

class WavelengthToRGB {
  private static final double gamma = 0.8;

   static Color getRBG (double wavelength) {
     double R = 0, G = 0, B = 0;
     if (wavelength >= 380 & wavelength <= 440) {
      double attenuation = 0.3 + 0.7 * (wavelength - 380) / (440 - 380);
      R = ((-(wavelength - 440) / (440 - 380)) * Math.pow(attenuation, gamma));
      B = Math.pow((1.0 * attenuation), gamma);
    } else if (wavelength >= 440 & wavelength <= 490) {
      G = Math.pow(((wavelength - 440) / (490 - 440)), gamma);
      B = 1.0;
    } else if (wavelength >= 490 & wavelength <= 510) {
      G = 1.0;
      B = Math.pow((-(wavelength - 510) / (510 - 490)), gamma);
    } else if (wavelength >= 510 & wavelength <= 580) {
      R = Math.pow(((wavelength - 510) / (580 - 510)), gamma);
      G = 1.0;
    } else if (wavelength >= 580 & wavelength <= 645) {
      R = 1.0;
      G = Math.pow((-(wavelength - 645) / (645 - 580)), gamma);
    } else if (wavelength >= 645 & wavelength <= 750) {
      double attenuation = 0.3 + 0.7 * (750 - wavelength) / (750 - 645);
      R = Math.pow((1.0 * attenuation), gamma);
    } else {
       return Color.white;
     }
    R = R * 255;
    G = G * 255;
    B = B * 255;
    return new Color((int) Math.floor(R), (int) Math.floor(G), (int) Math.floor(B));
  }
}