package jp.igapyon.mikumd2html.core;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import com.helger.css.ECSSVersion;
import com.helger.css.decl.AbstractHasTopLevelRules;
import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.decl.CSSExpression;
import com.helger.css.decl.CSSExpressionMemberFunction;
import com.helger.css.decl.CSSExpressionMemberTermURI;
import com.helger.css.decl.CSSFontFaceRule;
import com.helger.css.decl.CSSImportRule;
import com.helger.css.decl.CSSKeyframesRule;
import com.helger.css.decl.CSSUnknownRule;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.decl.IHasCSSDeclarations;
import com.helger.css.decl.ICSSExpressionMember;
import com.helger.css.decl.ICSSTopLevelRule;
import com.helger.css.reader.CSSReader;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.errorhandler.CollectingCSSParseErrorHandler;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Inspects supported raster and SVG formats; SVG CSS is parsed with the bundled ph-css dependency. */
public final class ImageInspector {
    public static final class Kind {
        private final String kind;
        private final String mime;
        Kind(String kind, String mime) { this.kind = kind; this.mime = mime; }
        public String getKind() { return kind; }
        public String getMime() { return mime; }
    }
    public static final class Info {
        private final String mime;
        private final long width;
        private final long height;
        Info(String mime, long width, long height) { this.mime = mime; this.width = width; this.height = height; }
        public String getMime() { return mime; }
        public long getWidth() { return width; }
        public long getHeight() { return height; }
    }

    private static final Set<String> SVG_ELEMENTS = new HashSet<String>(Arrays.asList(
            ("svg g defs title desc path rect circle ellipse line polyline polygon text tspan textPath use symbol linearGradient radialGradient stop clipPath mask marker pattern filter "
            + "feGaussianBlur feColorMatrix feOffset feBlend feComposite feFlood feMerge feMergeNode feMorphology feTurbulence feDisplacementMap "
            + "feConvolveMatrix feComponentTransfer feFuncR feFuncG feFuncB feFuncA feDiffuseLighting feSpecularLighting style feDistantLight "
            + "fePointLight feSpotLight feTile feImage image").split(" ")));
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

    /** Identify an image from a bounded prefix without validating its full contents. */
    public static Kind sniffImageKind(byte[] bytes) {
        if (bytes == null) throw new IllegalArgumentException("Image prefix is required.");
        String prefix = new String(bytes, 0, Math.min(bytes.length, 256), StandardCharsets.UTF_8).replaceFirst("^\\uFEFF", "").trim();
        if (prefix.startsWith("<svg") || prefix.startsWith("<?xml") || prefix.startsWith("<!--")
                || prefix.matches("(?is)^<!\\s*(?:doctype|entity).*$")) return new Kind("svg", "image/svg+xml");
        if (starts(bytes, new int[] { 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a })) return new Kind("raster", "image/png");
        if (starts(bytes, new int[] { 0xff, 0xd8, 0xff })) return new Kind("raster", "image/jpeg");
        if (bytes.length >= 6 && ascii(bytes, 0, 6).matches("GIF8[79]a")) return new Kind("raster", "image/gif");
        if (bytes.length >= 12 && "RIFF".equals(ascii(bytes, 0, 4)) && "WEBP".equals(ascii(bytes, 8, 4))) return new Kind("raster", "image/webp");
        return new Kind("unknown", null);
    }

    public static Info inspect(byte[] bytes, String expectedMime) {
        if (bytes == null || bytes.length == 0) throw invalid("Image data is empty.");
        String mime;
        long width;
        long height;
        if (starts(bytes, new int[] { 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a })) {
            mime = "image/png";
            if (bytes.length < 33) throw invalid("PNG is truncated.");
            checkPng(bytes);
            RasterDimensions.Size dimensions = RasterDimensions.read(bytes, mime);
            if (dimensions == null) throw invalid("Image dimensions could not be read.");
            width = dimensions.width; height = dimensions.height;
        } else if (starts(bytes, new int[] { 0xff, 0xd8, 0xff })) {
            mime = "image/jpeg";
            if (bytes.length < 4 || (bytes[bytes.length - 2] & 255) != 255 || (bytes[bytes.length - 1] & 255) != 217) throw invalid("JPEG is truncated.");
            RasterDimensions.Size dimensions = RasterDimensions.read(bytes, mime);
            if (dimensions == null) throw invalid("Image dimensions could not be read.");
            width = dimensions.width; height = dimensions.height;
        } else if (bytes.length >= 6 && ascii(bytes, 0, 6).matches("GIF8[79]a")) {
            mime = "image/gif";
            if (bytes.length < 14 || (bytes[bytes.length - 1] & 255) != 0x3b) throw invalid("GIF is truncated.");
            RasterDimensions.Size dimensions = RasterDimensions.read(bytes, mime);
            if (dimensions == null) throw invalid("Image dimensions could not be read.");
            width = dimensions.width; height = dimensions.height;
        } else if (bytes.length >= 12 && "RIFF".equals(ascii(bytes, 0, 4)) && "WEBP".equals(ascii(bytes, 8, 4))) {
            mime = "image/webp";
            long declared = (long) u32le(bytes, 4) + 8L;
            if (declared > bytes.length || bytes.length < 20) throw invalid("WebP is truncated.");
            RasterDimensions.Size dimensions = RasterDimensions.read(bytes, mime);
            if (dimensions == null) throw invalid("WebP header is invalid or unsupported.");
            width = dimensions.width; height = dimensions.height;
        } else if (looksLikeSvg(bytes)) {
            mime = "image/svg+xml";
            int[] dimensions = inspectSvg(bytes); width = dimensions[0]; height = dimensions[1];
        } else {
            throw new AssetAccessException("E_IMAGE_FORMAT", "The image format is unsupported or its header is invalid.");
        }
        if (expectedMime != null && !expectedMime.equals(mime)) throw invalid("Image data MIME does not match its file format.");
        if (!"image/svg+xml".equals(mime) && (width <= 0 || height <= 0)) throw invalid("Image dimensions are invalid.");
        if (width < 0 || height < 0) throw invalid("Image dimensions are invalid.");
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
        final String source;
        try {
            source = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new AssetAccessException("E_SVG_INVALID", "SVG is not valid UTF-8.", ex);
        }
        if (source.matches("(?is).*<!\\s*(DOCTYPE|ENTITY).*")) throw new AssetAccessException("E_SVG_UNSAFE", "SVG DTDs and entities are not supported.");
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
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(org.xml.sax.SAXParseException ex) throws SAXException { throw ex; }
                @Override public void fatalError(org.xml.sax.SAXParseException ex) throws SAXException { throw ex; }
            });
            org.w3c.dom.Document document = builder.parse(new ByteArrayInputStream(bytes));
            Element root = document.getDocumentElement();
            if (root == null || !"svg".equals(root.getLocalName()) || !"http://www.w3.org/2000/svg".equals(root.getNamespaceURI())) throw new AssetAccessException("E_SVG_INVALID", "SVG must have an SVG namespace root element.");
            for (Node child = document.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) throw new AssetAccessException("E_SVG_UNSAFE", "SVG processing instructions are not supported.");
            }
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
        String name = element.getLocalName();
        if (name == null) name = element.getNodeName();
        String normalizedName = name.toLowerCase(java.util.Locale.ROOT);
        if (!"http://www.w3.org/2000/svg".equals(element.getNamespaceURI()) || !SVG_ELEMENTS.contains(name)
                || "script".equals(normalizedName) || "foreignobject".equals(normalizedName)
                || normalizedName.startsWith("animate") || "set".equals(normalizedName) || "discard".equals(normalizedName)) {
            throw new AssetAccessException("E_SVG_UNSAFE", "SVG contains an unsupported or unsafe element: " + name + ".");
        }
        if ("style".equals(normalizedName)) validateSvgCss(element.getTextContent(), true);
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String attr = attribute.getNodeName().toLowerCase(java.util.Locale.ROOT);
            String value = attribute.getNodeValue().trim();
            if (attr.startsWith("on") || "xml:base".equals(attr) || !SVG_ATTRIBUTES.contains(attr)) throw new AssetAccessException("E_SVG_UNSAFE", "SVG contains an unsupported or unsafe attribute: " + attr + ".");
            if ("href".equals(attr) || "xlink:href".equals(attr)) {
                if (value.startsWith("#")) continue;
                if (!"image".equals(normalizedName) && !"feimage".equals(normalizedName)) {
                    throw new AssetAccessException("E_SVG_EXTERNAL_RESOURCE", "SVG external references are not supported.");
                }
                validateNestedRaster(value);
            }
            if ("style".equals(attr)) validateSvgCss(value, false);
            if ("fill".equals(attr) || "stroke".equals(attr) || "filter".equals(attr) || "clip-path".equals(attr)
                    || "mask".equals(attr) || attr.startsWith("marker-")) validateSvgCss(attr + ": " + value + ";", false);
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) if (child instanceof Element) validateSvg((Element) child);
    }

    private static void validateSvgCss(String css, boolean stylesheet) {
        try {
            CollectingCSSParseErrorHandler errors = new CollectingCSSParseErrorHandler();
            if (stylesheet) {
                CascadingStyleSheet sheet = CSSReader.readFromString(css, ECSSVersion.LATEST, errors);
                if (sheet == null || errors.hasParseErrors()) throw invalidSvgCss();
                if (sheet.hasImportRules()) throw externalSvgCss("SVG CSS imports, font loading, and animation are not supported.");
                inspectCssRules(sheet);
            } else {
                CSSDeclarationList declarations = CSSReaderDeclarationList.readFromString(css, ECSSVersion.LATEST, errors);
                if (declarations == null || errors.hasParseErrors()) throw invalidSvgCss();
                inspectCssDeclarations(declarations);
            }
        } catch (AssetAccessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw invalidSvgCss();
        }
    }

    private static void inspectCssRules(AbstractHasTopLevelRules rules) {
        for (ICSSTopLevelRule rule : rules.getAllRules()) {
            if (rule instanceof CSSFontFaceRule || rule instanceof CSSKeyframesRule) {
                throw externalSvgCss("SVG CSS imports, font loading, and animation are not supported.");
            }
            if (rule instanceof CSSImportRule) throw externalSvgCss("SVG CSS imports, font loading, and animation are not supported.");
            if (rule instanceof CSSUnknownRule) throw invalidSvgCss();
            if (rule instanceof IHasCSSDeclarations) inspectCssDeclarations((IHasCSSDeclarations<?>) rule);
            if (rule instanceof AbstractHasTopLevelRules) inspectCssRules((AbstractHasTopLevelRules) rule);
        }
    }

    private static void inspectCssDeclarations(IHasCSSDeclarations<?> container) {
        for (CSSDeclaration declaration : container.getAllDeclarations()) {
            String property = declaration.getProperty().toLowerCase(Locale.ROOT);
            if (property.matches("^animation(?:-|$).*")) throw new AssetAccessException("E_SVG_UNSUPPORTED", "SVG CSS animation is not supported.");
            inspectCssExpression(declaration.getExpression());
        }
    }

    private static void inspectCssDeclarations(CSSDeclarationList declarations) {
        for (CSSDeclaration declaration : declarations.getAllDeclarations()) {
            String property = declaration.getProperty().toLowerCase(Locale.ROOT);
            if (property.matches("^animation(?:-|$).*")) throw new AssetAccessException("E_SVG_UNSUPPORTED", "SVG CSS animation is not supported.");
            inspectCssExpression(declaration.getExpression());
        }
    }

    private static void inspectCssExpression(CSSExpression expression) {
        if (expression == null) return;
        for (ICSSExpressionMember member : expression.getAllMembers()) {
            if (member instanceof CSSExpressionMemberTermURI) {
                String uri = ((CSSExpressionMemberTermURI) member).getURIString();
                if (uri == null || !uri.matches("^#[^\\s'\"()]+$")) {
                    throw externalSvgCss("SVG CSS may only reference local fragment identifiers.");
                }
            }
            if (member instanceof CSSExpressionMemberFunction) {
                CSSExpressionMemberFunction function = (CSSExpressionMemberFunction) member;
                if (function.hasExpression()) inspectCssExpression(function.getExpression());
            }
        }
    }

    private static AssetAccessException invalidSvgCss() {
        return new AssetAccessException("E_SVG_UNSUPPORTED", "SVG contains invalid CSS.");
    }

    private static AssetAccessException externalSvgCss(String message) {
        return new AssetAccessException("E_SVG_EXTERNAL_RESOURCE", message);
    }

    private static void validateNestedRaster(String value) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?i)^data:(image/(?:png|jpeg|gif|webp));base64,([A-Za-z0-9+/]*={0,2})$").matcher(value);
        if (!matcher.matches()) throw new AssetAccessException("E_SVG_EXTERNAL_RESOURCE", "SVG image references must be embedded raster data URLs.");
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(matcher.group(2));
            if (bytes.length == 0 || !java.util.Base64.getEncoder().encodeToString(bytes).equals(matcher.group(2))) {
                throw invalid("SVG contains an invalid embedded raster image.");
            }
            inspect(bytes, matcher.group(1).toLowerCase(Locale.ROOT));
        } catch (AssetAccessException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw invalid("SVG contains an invalid embedded raster image.");
        }
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
