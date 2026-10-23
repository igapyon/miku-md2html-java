package jp.igapyon.mikumd2html.core;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Inspects the image formats accepted by the Node implementation using JDK APIs only. */
public final class ImageInspector {
    public static final class Info {
        private final String mime;
        private final int width;
        private final int height;
        Info(String mime, int width, int height) { this.mime = mime; this.width = width; this.height = height; }
        public String getMime() { return mime; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
    }

    private static final Set<String> SVG_ELEMENTS = new HashSet<String>(Arrays.asList(
            "svg", "g", "defs", "title", "desc", "path", "rect", "circle", "ellipse", "line", "polyline", "polygon",
            "text", "tspan", "textpath", "use", "symbol", "lineargradient", "radialgradient", "stop", "clippath", "mask",
            "marker", "pattern", "filter", "fegaussianblur", "fecolormatrix", "feoffset", "feblend", "fecomposite", "feflood",
            "femerge", "femergenode", "femorphology", "feturbulence", "fedisplacementmap", "feconvolvematrix", "fecomponenttransfer",
            "fefuncr", "fefuncg", "fefuncb", "fefunca", "fediffuselighting", "fespecularlighting", "style", "fedistantlight",
            "fepointlight", "fespotlight", "fetile", "feimage", "image"));
    private static final Set<String> SVG_ATTRIBUTES = new HashSet<String>(Arrays.asList(
            ("xmlns xmlns:xlink id class role aria-label aria-hidden viewbox preserveaspectratio width height x y x1 x2 y1 y2 cx cy r rx ry "
            + "d points pathlength fill fill-rule fill-opacity stroke stroke-width stroke-linecap stroke-linejoin stroke-miterlimit "
            + "stroke-dasharray stroke-dashoffset stroke-opacity opacity transform vector-effect clip-path clip-rule mask filter "
            + "marker-start marker-mid marker-end markerwidth markerheight refx refy orient gradientunits gradienttransform spreadmethod "
            + "offset stop-color stop-opacity patternunits patterncontentunits patterntransform font-family font-size font-style font-weight "
            + "text-anchor dominant-baseline alignment-baseline letter-spacing word-spacing dx dy rotate textlength lengthadjust startoffset "
            + "method spacing in in2 result type values stddeviation scale xchannelselector ychannelselector operator k1 k2 k3 k4 mode "
            + "color-interpolation-filters flood-color flood-opacity targetx targety basefrequency numoctaves seed stitchtiles surfacescale "
            + "diffuseconstant specularconstant specularexponent limitingconeangle azimuth elevation href xlink:href xml:space style")
                    .split(" ")));

    private ImageInspector() { }

    public static Info inspect(byte[] bytes, String expectedMime) {
        if (bytes == null || bytes.length == 0) throw invalid("Image data is empty.");
        String mime;
        int width;
        int height;
        if (starts(bytes, new int[] { 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a })) {
            mime = "image/png";
            if (bytes.length < 33) throw invalid("PNG is truncated.");
            width = int32be(bytes, 16); height = int32be(bytes, 20);
            checkPng(bytes);
        } else if (starts(bytes, new int[] { 0xff, 0xd8, 0xff })) {
            mime = "image/jpeg";
            int[] dimensions = jpegDimensions(bytes); width = dimensions[0]; height = dimensions[1];
            if (bytes.length < 4 || (bytes[bytes.length - 2] & 255) != 255 || (bytes[bytes.length - 1] & 255) != 217) throw invalid("JPEG is truncated.");
        } else if (ascii(bytes, 0, 6).matches("GIF8[79]a")) {
            mime = "image/gif";
            if (bytes.length < 14 || (bytes[bytes.length - 1] & 255) != 0x3b) throw invalid("GIF is truncated.");
            width = u16le(bytes, 6); height = u16le(bytes, 8);
        } else if (bytes.length >= 12 && "RIFF".equals(ascii(bytes, 0, 4)) && "WEBP".equals(ascii(bytes, 8, 4))) {
            mime = "image/webp";
            long declared = (long) u32le(bytes, 4) + 8L;
            if (declared > bytes.length || bytes.length < 30) throw invalid("WebP is truncated.");
            String chunk = ascii(bytes, 12, 4);
            if ("VP8X".equals(chunk)) {
                width = 1 + u24le(bytes, 24); height = 1 + u24le(bytes, 27);
            } else if ("VP8 ".equals(chunk) && (bytes[23] & 255) == 0x9d && (bytes[24] & 255) == 0x01 && (bytes[25] & 255) == 0x2a) {
                width = u16le(bytes, 26) & 0x3fff; height = u16le(bytes, 28) & 0x3fff;
            } else if ("VP8L".equals(chunk) && (bytes[20] & 255) == 0x2f) {
                width = 1 + (bytes[21] & 255) + ((bytes[22] & 0x3f) << 8);
                height = 1 + ((bytes[22] & 0xc0) >>> 6) + ((bytes[23] & 255) << 2) + ((bytes[24] & 0x0f) << 10);
            } else throw invalid("WebP header is invalid or unsupported.");
        } else if (looksLikeSvg(bytes)) {
            mime = "image/svg+xml";
            int[] dimensions = inspectSvg(bytes); width = dimensions[0]; height = dimensions[1];
        } else {
            throw new AssetAccessException("E_IMAGE_FORMAT", "The image format is unsupported or its header is invalid.");
        }
        if (expectedMime != null && !expectedMime.equals(mime)) throw invalid("Image data MIME does not match its file format.");
        if (width < 0 || height < 0 || ((width == 0) != (height == 0))) throw invalid("Image dimensions are invalid.");
        return new Info(mime, width, height);
    }

    private static void checkPng(byte[] bytes) {
        int offset = 8;
        while (offset + 12 <= bytes.length) {
            long size = int32be(bytes, offset) & 0xffffffffL;
            if (size > bytes.length - offset - 12L) throw invalid("PNG contains a truncated chunk.");
            String type = ascii(bytes, offset + 4, 4);
            offset += (int) size + 12;
            if ("IEND".equals(type)) {
                if (size != 0) throw invalid("PNG has an invalid IEND chunk.");
                return;
            }
        }
        throw invalid("PNG is truncated.");
    }

    private static int[] jpegDimensions(byte[] bytes) {
        int p = 2;
        while (p + 4 < bytes.length) {
            while (p < bytes.length && (bytes[p] & 255) != 0xff) p++;
            while (p < bytes.length && (bytes[p] & 255) == 0xff) p++;
            if (p >= bytes.length) break;
            int marker = bytes[p++] & 255;
            if (marker == 0xd9 || marker == 0xda) break;
            if (marker == 0x01 || (marker >= 0xd0 && marker <= 0xd7)) continue;
            if (p + 2 > bytes.length) break;
            int length = u16be(bytes, p);
            if (length < 2 || p + length > bytes.length) break;
            if ((marker >= 0xc0 && marker <= 0xc3) || (marker >= 0xc5 && marker <= 0xc7)
                    || (marker >= 0xc9 && marker <= 0xcb) || (marker >= 0xcd && marker <= 0xcf)) {
                if (length < 7) break;
                return new int[] { u16be(bytes, p + 5), u16be(bytes, p + 3) };
            }
            p += length;
        }
        throw invalid("JPEG dimensions could not be read.");
    }

    private static boolean looksLikeSvg(byte[] bytes) {
        String prefix = new String(bytes, 0, Math.min(bytes.length, 256), StandardCharsets.UTF_8).trim();
        return prefix.startsWith("<svg") || prefix.startsWith("<?xml") || prefix.startsWith("<!--");
    }

    private static int[] inspectSvg(byte[] bytes) {
        String source = new String(bytes, StandardCharsets.UTF_8);
        if (source.indexOf('\ufffd') >= 0 || source.matches("(?is).*<!\\s*(DOCTYPE|ENTITY).*")) throw new AssetAccessException("E_SVG_UNSAFE", "SVG must be UTF-8 without DTDs or entities.");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            org.w3c.dom.Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            Element root = document.getDocumentElement();
            if (root == null || !"svg".equals(root.getLocalName()) || !"http://www.w3.org/2000/svg".equals(root.getNamespaceURI())) throw new AssetAccessException("E_SVG_INVALID", "SVG must have an SVG namespace root element.");
            validateSvg(root);
            int width = svgDimension(root.getAttribute("width"));
            int height = svgDimension(root.getAttribute("height"));
            return new int[] { width, height };
        } catch (AssetAccessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AssetAccessException("E_SVG_INVALID", "SVG XML is invalid or could not be safely inspected.", ex);
        }
    }

    private static void validateSvg(Element element) {
        String name = element.getLocalName().toLowerCase(java.util.Locale.ROOT);
        if (!SVG_ELEMENTS.contains(name) || "script".equals(name) || "foreignobject".equals(name)
                || name.startsWith("animate") || "set".equals(name) || "discard".equals(name)) {
            throw new AssetAccessException("E_SVG_UNSAFE", "SVG contains an unsupported or unsafe element: " + name + ".");
        }
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String attr = attribute.getNodeName().toLowerCase(java.util.Locale.ROOT);
            String value = attribute.getNodeValue().trim();
            if (attr.startsWith("on") || "xml:base".equals(attr) || !SVG_ATTRIBUTES.contains(attr)) throw new AssetAccessException("E_SVG_UNSAFE", "SVG contains an unsupported or unsafe attribute: " + attr + ".");
            if ("href".equals(attr) || "xlink:href".equals(attr)) {
                if (!value.startsWith("#") && !value.matches("(?is)data:image/(?:png|jpeg|gif|webp);base64,[A-Za-z0-9+/]+={0,2}")) throw new AssetAccessException("E_SVG_EXTERNAL_RESOURCE", "SVG external references are not supported.");
            }
            if ("style".equals(attr) || "style".equals(name)) {
                String css = "style".equals(name) ? element.getTextContent() : value;
                if (css.matches("(?is).*(@import|expression\\s*\\(|javascript:|url\\s*\\(\\s*(['\\\"]?)(?!#)).*")) throw new AssetAccessException("E_SVG_EXTERNAL_RESOURCE", "SVG CSS may only use local fragment references.");
            }
            if (("fill".equals(attr) || "stroke".equals(attr) || attr.endsWith("-path"))
                    && value.toLowerCase(java.util.Locale.ROOT).contains("url(") && !value.matches("(?i)url\\(\\s*['\\\"]?#[-A-Za-z0-9_.:]+['\\\"]?\\s*\\)")) {
                throw new AssetAccessException("E_SVG_EXTERNAL_RESOURCE", "SVG paint references must be local fragments.");
            }
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) if (child instanceof Element) validateSvg((Element) child);
    }

    private static int svgDimension(String value) {
        try { return (int) Math.ceil(Double.parseDouble(value.replaceFirst("(?i)px$", ""))); }
        catch (Exception ignored) { return 0; }
    }
    private static boolean starts(byte[] b, int[] values) { if (b.length < values.length) return false; for (int i = 0; i < values.length; i++) if ((b[i] & 255) != values[i]) return false; return true; }
    private static int int32be(byte[] b, int p) { return (b[p] & 255) << 24 | (b[p + 1] & 255) << 16 | (b[p + 2] & 255) << 8 | b[p + 3] & 255; }
    private static int u16be(byte[] b, int p) { return (b[p] & 255) << 8 | b[p + 1] & 255; }
    private static int u16le(byte[] b, int p) { return b[p] & 255 | (b[p + 1] & 255) << 8; }
    private static long u32le(byte[] b, int p) { return (b[p] & 255L) | (b[p + 1] & 255L) << 8 | (b[p + 2] & 255L) << 16 | (b[p + 3] & 255L) << 24; }
    private static int u24le(byte[] b, int p) { return b[p] & 255 | (b[p + 1] & 255) << 8 | (b[p + 2] & 255) << 16; }
    private static String ascii(byte[] b, int p, int length) { return new String(b, p, length, StandardCharsets.US_ASCII); }
    private static AssetAccessException invalid(String message) { return new AssetAccessException("E_IMAGE_INVALID", message); }
}
