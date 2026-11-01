import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import javax.imageio.ImageIO;

/** Test-only QR generator; input contains no token or media metadata. */
public final class QrImage {
    public static void main(String[] args) throws Exception {
        BitMatrix bits = new MultiFormatWriter().encode(args[0], BarcodeFormat.QR_CODE, 512, 512, Map.of(EncodeHintType.MARGIN, 4));
        BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++) image.setRGB(x, y, bits.get(x, y) ? 0xff000000 : 0xffffffff);
        ImageIO.write(image, "png", new File(args[1]));
        int[] pixels = image.getRGB(0, 0, 512, 512, null, 0, 512);
        String decoded = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(512, 512, pixels)))).getText();
        if (!decoded.equals(args[0])) throw new IllegalStateException("QR round-trip failed");
    }
}
