/*
  Adapted from https://algs4.cs.princeton.edu/14analysis/PolynomialRegression.java
 */
public class PolynomialRegression {

  static class QRDecomposition {
    private double[][] qr;
    private int rows, cols;
    private double[] Rdiag;

    /**
     * QR Decomposition, computed by Householder reflections.
     * @param A double[][] matrix
     */

    QRDecomposition (double[][] A) {
      qr = A;
      rows = qr.length;
      cols = qr[0].length;
      Rdiag = new double[cols];
      for (int kk = 0; kk < cols; kk++) {
        // Compute 2-norm of kk-th column without under/overflow.
        double nrm = 0;
        for (int ii = kk; ii < rows; ii++) {
          // sqrt(a^2 + b^2) without under/overflow.
          double r;
          if (Math.abs(nrm) > Math.abs(qr[ii][kk])) {
            r = qr[ii][kk] / nrm;
            r = Math.abs(nrm) * Math.sqrt(1 + r * r);
          } else if (qr[ii][kk] != 0) {
            r = nrm / qr[ii][kk];
            r = Math.abs(qr[ii][kk]) * Math.sqrt(1 + r * r);
          } else {
            r = 0.0;
          }
          nrm = r;
        }
        if (nrm != 0.0) {
          // Form kk-th Householder vector.
          if (qr[kk][kk] < 0) {
            nrm = -nrm;
          }
          for (int ii = kk; ii < rows; ii++) {
            qr[ii][kk] /= nrm;
          }
          qr[kk][kk] += 1.0;
          // Apply transformation to remaining columns.
          for (int jj = kk + 1; jj < cols; jj++) {
            double s = 0.0;
            for (int ii = kk; ii < rows; ii++) {
              s += qr[ii][kk] * qr[ii][jj];
            }
            s = -s / qr[kk][kk];
            for (int ii = kk; ii < rows; ii++) {
              qr[ii][jj] += s * qr[ii][kk];
            }
          }
        }
        Rdiag[kk] = -nrm;
      }
    }

    /**
     * Is the matrix full rank?
     *
     * @return true if R, and hence A, has full rank.
     */

    boolean isFullRank () {
      for (int jj = 0; jj < qr[0].length; jj++) {
        if (Rdiag[jj] == 0)
          return false;
      }
      return true;
    }
  }

  /**
   * Performs a polynomial reggression on the data points
   *
   * @param xy  predictor in pairs[n][0] and corresponding response in pairs[n][1]
   * @param degree the degree of the polynomial to fit
   */
  private static double[] getCoefficients (double[][] xy, int degree) {
    QRDecomposition qr;
    // in case Vandermonde matrix does not have full rank, reduce degree until it does
    while (true) {
      // build Vandermonde matrix
      double[][] xMatrix = new double[xy.length][degree + 1];
      for (int ii = 0; ii < xy.length; ii++) {
        for (int jj = 0; jj <= degree; jj++) {
          xMatrix[ii][jj] = Math.pow(xy[ii][0], jj);
        }
      }
      // find least squares solution
      qr = new QRDecomposition(xMatrix);
      if (qr.isFullRank()) {
        break;
      }
      // decrease degree and try again
      degree--;
    }
    // Copy right hand side
    // Compute Y = transpose(Q) * B
    for (int kk = 0; kk < qr.cols; kk++) {
      double s = 0.0;
      for (int ii = kk; ii < qr.rows; ii++) {
        s += qr.qr[ii][kk] * xy[ii][1];
      }
      s = -s / qr.qr[kk][kk];
      for (int ii = kk; ii < qr.rows; ii++) {
        xy[ii][1] += s * qr.qr[ii][kk];
      }
    }
    // Solve R * X = Y;
    for (int kk = qr.cols - 1; kk >= 0; kk--) {
      xy[kk][1] /= qr.Rdiag[kk];
      for (int ii = 0; ii < kk; ii++) {
        xy[ii][1] -= xy[kk][1] * qr.qr[ii][kk];
      }
    }
    // copy polynomial regression coefficients
    double[] coeff = new double[degree + 1];
    for (int ii = 0; ii < coeff.length; ii++) {
      coeff[ii] = xy[ii][1];
    }
    return coeff;
  }

  /**
   * Test data samples from "USB4000 Operating Instructions.pdf" page 15
   * 0: 190.37722111132746
   * 1:   0.36315951123072354
   * 2:  -1.2463449040608586E-5
   * 3:  -2.2475147642325247E-9
   */

  public static void main (String[] args) {
    double[][] pairs = {
        { 175, 253.65}, { 296, 296.73}, { 312, 302.15}, { 342, 313.16}, { 402, 334.15},
        { 490, 365.02}, { 604, 404.66}, { 613, 407.78}, { 694, 435.84}, {1022, 546.07},
        {1116, 576.96}, {1122, 579.07}, {1491, 696.54}, {1523, 706.72}, {1590, 727.29},
        {1627, 738.40}, {1669, 751.47},
    };
    double[] beta = getCoefficients(pairs, 3);
    for (int ii = 0; ii < beta.length; ii++) {
      System.out.println(ii + ": " + beta[ii]);
    }
  }
}
