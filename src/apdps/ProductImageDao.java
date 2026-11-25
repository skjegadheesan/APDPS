package apdps;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Product photos. The admin picks a picture file; it is shrunk to at most 800 x 800 pixels, saved as JPEG
 * and stored in the BLOB column product_image.imageData (one picture per product).
 * Buyers and the admin read the pictures back from the database; nothing is kept on disk.
 */
public final class ProductImageDao {
    /** Largest picture file the admin may choose. */
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_SIDE = 800;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final int IN_LIST_LIMIT = 500;   // Oracle allows at most 1000 values in IN (...)

    private ProductImageDao() {
    }

    /** Reads a picture file, checks it and returns the shrunk JPEG bytes to store. */
    public static byte[] prepare(File file) throws IOException {
        if (file.length() > MAX_FILE_BYTES) {
            throw new IOException("The picture is larger than 5 MB. Choose a smaller picture.");
        }
        checkDimensions(file);
        BufferedImage source = ImageIO.read(file);
        if (source == null) {
            throw new IOException("This file is not a picture. Choose a JPG, PNG or GIF file.");
        }
        int w = source.getWidth();
        int h = source.getHeight();
        source = upright(source, exifOrientation(file));
        w = source.getWidth();
        h = source.getHeight();
        double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        // JPEG has no transparency, so draw on a white background
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, nw, nh);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, nw, nh, null);
        g.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(out, "jpg", bytes)) {
            throw new IOException("The picture could not be converted.");
        }
        return bytes.toByteArray();
    }

    /**
     * The orientation stored by a phone or camera in a JPEG file (EXIF tag 0x0112): 1 = as it is,
     * 3 = upside down, 6 = turn right, 8 = turn left (2, 4, 5, 7 are mirrored versions). Returns 1 when
     * the file has none or cannot be read.
     */
    static int exifOrientation(File file) {
        try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(file, "r")) {
            byte[] b = new byte[(int) Math.min(f.length(), 128 * 1024)];
            f.readFully(b);
            if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
                return 1;   // not a JPEG
            }
            int pos = 2;
            while (pos + 4 <= b.length && (b[pos] & 0xFF) == 0xFF) {
                int marker = b[pos + 1] & 0xFF;
                int length = ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
                if (marker == 0xDA || length < 2) {
                    return 1;   // picture data starts: no EXIF
                }
                if (marker == 0xE1 && pos + 4 + 6 <= b.length && b[pos + 4] == 'E' && b[pos + 5] == 'x'
                        && b[pos + 6] == 'i' && b[pos + 7] == 'f') {
                    int tiff = pos + 10;
                    boolean little = b[tiff] == 'I';
                    int ifd = tiff + (int) readInt(b, tiff + 4, 4, little);
                    int entries = (int) readInt(b, ifd, 2, little);
                    for (int i = 0; i < entries; i++) {
                        int entry = ifd + 2 + i * 12;
                        if (readInt(b, entry, 2, little) == 0x0112) {
                            int value = (int) readInt(b, entry + 8, 2, little);
                            return value >= 1 && value <= 8 ? value : 1;
                        }
                    }
                    return 1;
                }
                pos += 2 + length;
            }
        } catch (IOException | RuntimeException ex) {
            // damaged or unusual metadata: just show the picture as it is
        }
        return 1;
    }

    private static long readInt(byte[] b, int at, int size, boolean little) {
        long v = 0;
        for (int i = 0; i < size; i++) {
            int x = b[at + (little ? size - 1 - i : i)] & 0xFF;
            v = (v << 8) | x;
        }
        return v;
    }

    /** Turns / mirrors the picture so that it is upright, following the EXIF orientation number. */
    static BufferedImage upright(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        java.awt.geom.AffineTransform t;
        switch (orientation) {
            case 2: t = new java.awt.geom.AffineTransform(-1, 0, 0, 1, w, 0); break;
            case 3: t = new java.awt.geom.AffineTransform(-1, 0, 0, -1, w, h); break;
            case 4: t = new java.awt.geom.AffineTransform(1, 0, 0, -1, 0, h); break;
            case 5: t = new java.awt.geom.AffineTransform(0, 1, 1, 0, 0, 0); break;
            case 6: t = new java.awt.geom.AffineTransform(0, 1, -1, 0, h, 0); break;
            case 7: t = new java.awt.geom.AffineTransform(0, -1, -1, 0, h, w); break;
            default: t = new java.awt.geom.AffineTransform(0, -1, 1, 0, 0, w); break;   // 8
        }
        boolean swap = orientation >= 5;
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(src, t, null);
        g.dispose();
        return out;
    }

    /** Reads only the size of the picture, so a tiny file that expands to a gigantic image is refused early. */
    private static void checkDimensions(File file) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            if (in == null) {
                return;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return;   // not a picture: prepare() reports that
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new IOException("The picture is too large (" + reader.getWidth(0) + " x "
                        + reader.getHeight(0) + " pixels). Choose a picture of at most 40 megapixels.");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    /** Saves (or replaces) the picture of a product. */
    public static void save(int productId, String fileName, byte[] jpeg) throws SQLException {
        String name = fileName == null ? null : (fileName.length() > 255 ? fileName.substring(0, 255) : fileName);
        try (Connection con = DBConnection.getConnection()) {
            int changed;
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE product_image SET fileName = ?, imageData = ?, uploadedAt = SYSTIMESTAMP WHERE productId = ?")) {
                ps.setString(1, name);
                ps.setBinaryStream(2, new ByteArrayInputStream(jpeg), jpeg.length);
                ps.setInt(3, productId);
                changed = ps.executeUpdate();
            }
            if (changed == 0) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO product_image (productId, fileName, imageData) VALUES (?, ?, ?)")) {
                    ps.setInt(1, productId);
                    ps.setString(2, name);
                    ps.setBinaryStream(3, new ByteArrayInputStream(jpeg), jpeg.length);
                    ps.executeUpdate();
                }
            }
        }
    }

    /** Removes the picture of a product; returns false when it had none. */
    public static boolean delete(int productId) throws SQLException {
        return AppUtil.update("DELETE FROM product_image WHERE productId = ?", productId) > 0;
    }

    /** The picture of one product, or null when it has none. */
    public static BufferedImage load(int productId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT imageData FROM product_image WHERE productId = ?")) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? decode(rs) : null;
            }
        }
    }

    /**
     * Small icons (fitted into width x height) of the pictures of several products. The ids are sent in groups of
     * 500 (Oracle refuses more than 1000 values in IN (...)), and each picture is shrunk as soon as it is read, so
     * only one full-size picture is in memory at a time. Products without a picture are not in the map.
     */
    public static Map<Integer, ImageIcon> loadScaled(List<Integer> productIds, int width, int height) throws SQLException {
        Map<Integer, ImageIcon> result = new HashMap<>();
        for (int from = 0; from < productIds.size(); from += IN_LIST_LIMIT) {
            List<Integer> part = productIds.subList(from, Math.min(from + IN_LIST_LIMIT, productIds.size()));
            StringBuilder marks = new StringBuilder();
            for (int i = 0; i < part.size(); i++) {
                marks.append(i == 0 ? "?" : ",?");
            }
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                     "SELECT productId, imageData FROM product_image WHERE productId IN (" + marks + ")")) {
                for (int i = 0; i < part.size(); i++) {
                    ps.setInt(i + 1, part.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        BufferedImage image = decode(rs);
                        if (image != null) {
                            result.put(rs.getInt(1), fit(image, width, height));
                        }
                    }
                }
            }
        }
        return result;
    }

    private static BufferedImage decode(ResultSet rs) throws SQLException {
        try (InputStream in = rs.getBinaryStream("imageData")) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException ex) {
            return null;   // a damaged picture is shown as "No photo"
        }
    }

    /** The picture shrunk to fit inside width x height (the proportions are kept; never enlarged beyond the box). */
    public static ImageIcon fit(BufferedImage image, int width, int height) {
        double scale = Math.min((double) width / image.getWidth(), (double) height / image.getHeight());
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage current = toRgb(image);
        // shrink by at most half at a time: one big jump with bilinear filtering gives jagged edges
        while (current.getWidth() / 2 >= w && current.getHeight() / 2 >= h) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        if (current.getWidth() != w || current.getHeight() != h) {
            current = resize(current, w, h);
        }
        return new ImageIcon(current);
    }

    private static BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage resize(BufferedImage image, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
