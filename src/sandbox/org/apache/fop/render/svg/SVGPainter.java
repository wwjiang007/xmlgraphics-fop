/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id$ */

package org.apache.fop.render.svg;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import org.w3c.dom.Document;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.xmlgraphics.image.loader.ImageException;
import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.ImageManager;
import org.apache.xmlgraphics.image.loader.ImageSessionContext;
import org.apache.xmlgraphics.util.QName;
import org.apache.xmlgraphics.xmp.Metadata;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.MimeConstants;
import org.apache.fop.events.ResourceEventProducer;
import org.apache.fop.fo.extensions.ExtensionElementMapping;
import org.apache.fop.render.RenderingContext;
import org.apache.fop.render.intermediate.AbstractIFPainter;
import org.apache.fop.render.intermediate.IFConstants;
import org.apache.fop.render.intermediate.IFException;
import org.apache.fop.render.intermediate.IFState;
import org.apache.fop.render.intermediate.IFUtil;
import org.apache.fop.traits.BorderProps;
import org.apache.fop.traits.RuleStyle;
import org.apache.fop.util.ColorUtil;
import org.apache.fop.util.GenerationHelperContentHandler;
import org.apache.fop.util.XMLUtil;

/**
 * IFPainter implementation that writes SVG.
 */
public class SVGPainter extends AbstractIFPainter implements SVGConstants {

    /** logging instance */
    private static Log log = LogFactory.getLog(SVGPainter.class);

    /** Holds the intermediate format state */
    protected IFState state;

    private AbstractSVGDocumentHandler parent;

    /** The SAX content handler that receives the generated XML events. */
    protected GenerationHelperContentHandler handler;

    private static final int MODE_NORMAL = 0;
    private static final int MODE_TEXT = 1;

    private int mode = MODE_NORMAL;

    /**
     * Main constructor.
     * @param parent the parent document handler
     * @param contentHandler the target SAX content handler
     */
    public SVGPainter(AbstractSVGDocumentHandler parent,
            GenerationHelperContentHandler contentHandler) {
        super();
        this.parent = parent;
        this.handler = contentHandler;
        this.state = IFState.create();
    }

    /** {@inheritDoc} */
    protected FOUserAgent getUserAgent() {
        return parent.getUserAgent();
    }

    /** {@inheritDoc} */
    public void startViewport(AffineTransform transform, Dimension size, Rectangle clipRect)
            throws IFException {
        startViewport(IFUtil.toString(transform), size, clipRect);
    }

    /** {@inheritDoc} */
    public void startViewport(AffineTransform[] transforms, Dimension size, Rectangle clipRect)
            throws IFException {
        startViewport(IFUtil.toString(transforms), size, clipRect);
    }

    private void startViewport(String transform, Dimension size, Rectangle clipRect)
            throws IFException {
        try {
            establish(MODE_NORMAL);
            AttributesImpl atts = new AttributesImpl();
            if (transform != null && transform.length() > 0) {
                XMLUtil.addAttribute(atts, "transform", transform);
            }
            handler.startElement("g", atts);

            atts.clear();
            XMLUtil.addAttribute(atts, "width", Integer.toString(size.width));
            XMLUtil.addAttribute(atts, "height", Integer.toString(size.height));
            if (clipRect != null) {
                int[] v = new int[] {
                        clipRect.y,
                        -clipRect.x + size.width - clipRect.width,
                        -clipRect.y + size.height - clipRect.height,
                        clipRect.x};
                int sum = 0;
                for (int i = 0; i < 4; i++) {
                    sum += Math.abs(v[i]);
                }
                if (sum != 0) {
                    StringBuffer sb = new StringBuffer("rect(");
                    sb.append(v[0]).append(',');
                    sb.append(v[1]).append(',');
                    sb.append(v[2]).append(',');
                    sb.append(v[3]).append(')');
                    XMLUtil.addAttribute(atts, "clip", sb.toString());
                }
                XMLUtil.addAttribute(atts, "overflow", "hidden");
            } else {
                XMLUtil.addAttribute(atts, "overflow", "visible");
            }
            handler.startElement("svg", atts);
        } catch (SAXException e) {
            throw new IFException("SAX error in startBox()", e);
        }
    }

    /** {@inheritDoc} */
    public void endViewport() throws IFException {
        try {
            establish(MODE_NORMAL);
            handler.endElement("svg");
            handler.endElement("g");
        } catch (SAXException e) {
            throw new IFException("SAX error in endBox()", e);
        }
    }

    /** {@inheritDoc} */
    public void startGroup(AffineTransform[] transforms) throws IFException {
        startGroup(IFUtil.toString(transforms));
    }

    /** {@inheritDoc} */
    public void startGroup(AffineTransform transform) throws IFException {
        startGroup(IFUtil.toString(transform));
    }

    private void startGroup(String transform) throws IFException {
        try {
            AttributesImpl atts = new AttributesImpl();
            if (transform != null && transform.length() > 0) {
                XMLUtil.addAttribute(atts, "transform", transform);
            }
            handler.startElement("g", atts);
        } catch (SAXException e) {
            throw new IFException("SAX error in startGroup()", e);
        }
    }

    /** {@inheritDoc} */
    public void endGroup() throws IFException {
        try {
            establish(MODE_NORMAL);
            handler.endElement("g");
        } catch (SAXException e) {
            throw new IFException("SAX error in endGroup()", e);
        }
    }

    private static final QName CONVERSION_MODE
            = new QName(ExtensionElementMapping.URI, null, "conversion-mode");

    /** {@inheritDoc} */
    public void drawImage(String uri, Rectangle rect, Map foreignAttributes) throws IFException {
        try {
            establish(MODE_NORMAL);

            ImageManager manager = getUserAgent().getFactory().getImageManager();
            ImageInfo info = null;
            try {
                ImageSessionContext sessionContext = getUserAgent().getImageSessionContext();
                info = manager.getImageInfo(uri, sessionContext);

                String mime = info.getMimeType();
                String conversionMode = (String)foreignAttributes.get(CONVERSION_MODE);
                if ("reference".equals(conversionMode)
                        && (MimeConstants.MIME_GIF.equals(mime)
                        || MimeConstants.MIME_JPEG.equals(mime)
                        || MimeConstants.MIME_PNG.equals(mime)
                        || MimeConstants.MIME_SVG.equals(mime))) {
                    //Just reference the image
                    //TODO Some additional URI rewriting might be necessary
                    AttributesImpl atts = new AttributesImpl();
                    XMLUtil.addAttribute(atts, IFConstants.XLINK_HREF, uri);
                    XMLUtil.addAttribute(atts, "x", Integer.toString(rect.x));
                    XMLUtil.addAttribute(atts, "y", Integer.toString(rect.y));
                    XMLUtil.addAttribute(atts, "width", Integer.toString(rect.width));
                    XMLUtil.addAttribute(atts, "height", Integer.toString(rect.height));
                    handler.element("image", atts);
                } else {
                    drawImageUsingImageHandler(info, rect);
                }
            } catch (ImageException ie) {
                ResourceEventProducer eventProducer = ResourceEventProducer.Provider.get(
                        getUserAgent().getEventBroadcaster());
                eventProducer.imageError(this, (info != null ? info.toString() : uri), ie, null);
            } catch (FileNotFoundException fe) {
                ResourceEventProducer eventProducer = ResourceEventProducer.Provider.get(
                        getUserAgent().getEventBroadcaster());
                eventProducer.imageNotFound(this, (info != null ? info.toString() : uri), fe, null);
            } catch (IOException ioe) {
                ResourceEventProducer eventProducer = ResourceEventProducer.Provider.get(
                        getUserAgent().getEventBroadcaster());
                eventProducer.imageIOError(this, (info != null ? info.toString() : uri), ioe, null);
            }
        } catch (SAXException e) {
            throw new IFException("SAX error in drawImage()", e);
        }
    }

    /** {@inheritDoc} */
    public void drawImage(Document doc, Rectangle rect, Map foreignAttributes) throws IFException {
        try {
            establish(MODE_NORMAL);

            drawImageUsingDocument(doc, rect);
        } catch (SAXException e) {
            throw new IFException("SAX error in drawImage()", e);
        }
    }

    /** {@inheritDoc} */
    protected RenderingContext createRenderingContext() {
        SVGRenderingContext svgContext = new SVGRenderingContext(
                getUserAgent(), handler);
        return svgContext;
    }

    private static String toString(Paint paint) {
        //TODO Paint serialization: Fine-tune and extend!
        if (paint instanceof Color) {
            return ColorUtil.colorToString((Color)paint);
        } else {
            throw new UnsupportedOperationException("Paint not supported: " + paint);
        }
    }

    /** {@inheritDoc} */
    public void clipRect(Rectangle rect) throws IFException {
        //TODO Implement me!!!
    }

    /** {@inheritDoc} */
    public void fillRect(Rectangle rect, Paint fill) throws IFException {
        if (fill == null) {
            return;
        }
        try {
            establish(MODE_NORMAL);
            AttributesImpl atts = new AttributesImpl();
            XMLUtil.addAttribute(atts, "x", Integer.toString(rect.x));
            XMLUtil.addAttribute(atts, "y", Integer.toString(rect.y));
            XMLUtil.addAttribute(atts, "width", Integer.toString(rect.width));
            XMLUtil.addAttribute(atts, "height", Integer.toString(rect.height));
            if (fill != null) {
                XMLUtil.addAttribute(atts, "fill", toString(fill));
            }
            /* disabled
            if (stroke != null) {
                XMLUtil.addAttribute(atts, "stroke", toString(stroke));
            }*/
            handler.element("rect", atts);
        } catch (SAXException e) {
            throw new IFException("SAX error in fillRect()", e);
        }
    }

    /** {@inheritDoc} */
    public void drawBorderRect(Rectangle rect, BorderProps before, BorderProps after,
            BorderProps start, BorderProps end) throws IFException {
        // TODO Auto-generated method stub
    }

    /** {@inheritDoc} */
    public void drawLine(Point start, Point end, int width, Color color, RuleStyle style)
            throws IFException {
        try {
            establish(MODE_NORMAL);
            AttributesImpl atts = new AttributesImpl();
            XMLUtil.addAttribute(atts, "x1", Integer.toString(start.x));
            XMLUtil.addAttribute(atts, "y1", Integer.toString(start.y));
            XMLUtil.addAttribute(atts, "x2", Integer.toString(end.x));
            XMLUtil.addAttribute(atts, "y2", Integer.toString(end.y));
            XMLUtil.addAttribute(atts, "stroke-width", toString(color));
            XMLUtil.addAttribute(atts, "fill", toString(color));
            //TODO Handle style parameter
            handler.element("line", atts);
        } catch (SAXException e) {
            throw new IFException("SAX error in drawLine()", e);
        }
    }

    /** {@inheritDoc} */
    public void drawText(int x, int y, int[] dx, int[] dy, String text) throws IFException {
        try {
            establish(MODE_TEXT);
            AttributesImpl atts = new AttributesImpl();
            XMLUtil.addAttribute(atts, "x", Integer.toString(x));
            XMLUtil.addAttribute(atts, "y", Integer.toString(y));
            if (dx != null) {
                XMLUtil.addAttribute(atts, "dx", IFUtil.toString(dx));
            }
            if (dy != null) {
                XMLUtil.addAttribute(atts, "dy", IFUtil.toString(dy));
            }
            handler.startElement("text", atts);
            char[] chars = text.toCharArray();
            handler.characters(chars, 0, chars.length);
            handler.endElement("text");
        } catch (SAXException e) {
            throw new IFException("SAX error in setFont()", e);
        }
    }

    /** {@inheritDoc} */
    public void setFont(String family, String style, Integer weight, String variant, Integer size,
            Color color) throws IFException {
        if (family != null) {
            state.setFontFamily(family);
        }
        if (style != null) {
            state.setFontStyle(style);
        }
        if (weight != null) {
            state.setFontWeight(weight.intValue());
        }
        if (variant != null) {
            state.setFontVariant(variant);
        }
        if (size != null) {
            state.setFontSize(size.intValue());
        }
        if (color != null) {
            state.setTextColor(color);
        }
    }

    private void leaveTextMode() throws SAXException {
        assert this.mode == MODE_TEXT;
        handler.endElement("g");
        this.mode = MODE_NORMAL;
    }

    private void establish(int newMode) throws SAXException {
        switch (newMode) {
        case MODE_TEXT:
            enterTextMode();
            break;
        default:
            if (this.mode == MODE_TEXT) {
                leaveTextMode();
            }
        }
    }

    private void enterTextMode() throws SAXException {
        if (state.isFontChanged() && this.mode == MODE_TEXT) {
            leaveTextMode();
        }
        if (this.mode != MODE_TEXT) {
            startTextGroup();
            this.mode = MODE_TEXT;
        }
    }

    private void startTextGroup() throws SAXException {
        AttributesImpl atts = new AttributesImpl();
        XMLUtil.addAttribute(atts, "font-family", state.getFontFamily());
        XMLUtil.addAttribute(atts, "font-style", state.getFontStyle());
        XMLUtil.addAttribute(atts, "font-weight", Integer.toString(state.getFontWeight()));
        XMLUtil.addAttribute(atts, "font-variant", state.getFontVariant());
        XMLUtil.addAttribute(atts, "font-size", Integer.toString(state.getFontSize()));
        XMLUtil.addAttribute(atts, "fill", toString(state.getTextColor()));
        handler.startElement("g", atts);
        state.resetFontChanged();
    }

    /** {@inheritDoc} */
    public void handleExtensionObject(Object extension) throws IFException {
        if (extension instanceof Metadata) {
            Metadata meta = (Metadata)extension;
            try {
                establish(MODE_NORMAL);
                handler.startElement("metadata");
                meta.toSAX(this.handler);
                handler.endElement("metadata");
            } catch (SAXException e) {
                throw new IFException("SAX error while handling extension object", e);
            }
        } else {
            throw new UnsupportedOperationException(
                    "Don't know how to handle extension object: " + extension);
        }
    }

}