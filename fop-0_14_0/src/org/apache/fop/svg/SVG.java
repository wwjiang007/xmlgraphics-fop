/*-- $Id$ -- 

 ============================================================================
                   The Apache Software License, Version 1.1
 ============================================================================
 
    Copyright (C) 1999 The Apache Software Foundation. All rights reserved.
 
 Redistribution and use in source and binary forms, with or without modifica-
 tion, are permitted provided that the following conditions are met:
 
 1. Redistributions of  source code must  retain the above copyright  notice,
    this list of conditions and the following disclaimer.
 
 2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.
 
 3. The end-user documentation included with the redistribution, if any, must
    include  the following  acknowledgment:  "This product includes  software
    developed  by the  Apache Software Foundation  (http://www.apache.org/)."
    Alternately, this  acknowledgment may  appear in the software itself,  if
    and wherever such third-party acknowledgments normally appear.
 
 4. The names "FOP" and  "Apache Software Foundation"  must not be used to
    endorse  or promote  products derived  from this  software without  prior
    written permission. For written permission, please contact
    apache@apache.org.
 
 5. Products  derived from this software may not  be called "Apache", nor may
    "Apache" appear  in their name,  without prior written permission  of the
    Apache Software Foundation.
 
 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 FITNESS  FOR A PARTICULAR  PURPOSE ARE  DISCLAIMED.  IN NO  EVENT SHALL  THE
 APACHE SOFTWARE  FOUNDATION  OR ITS CONTRIBUTORS  BE LIABLE FOR  ANY DIRECT,
 INDIRECT, INCIDENTAL, SPECIAL,  EXEMPLARY, OR CONSEQUENTIAL  DAMAGES (INCLU-
 DING, BUT NOT LIMITED TO, PROCUREMENT  OF SUBSTITUTE GOODS OR SERVICES; LOSS
 OF USE, DATA, OR  PROFITS; OR BUSINESS  INTERRUPTION)  HOWEVER CAUSED AND ON
 ANY  THEORY OF LIABILITY,  WHETHER  IN CONTRACT,  STRICT LIABILITY,  OR TORT
 (INCLUDING  NEGLIGENCE OR  OTHERWISE) ARISING IN  ANY WAY OUT OF THE  USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 
 This software  consists of voluntary contributions made  by many individuals
 on  behalf of the Apache Software  Foundation and was  originally created by
 James Tauber <jtauber@jtauber.com>. For more  information on the Apache 
 Software Foundation, please see <http://www.apache.org/>.
 
 */

package org.apache.fop.svg;

// FOP
import org.apache.fop.fo.*;
import org.apache.fop.fo.properties.*;
import org.apache.fop.layout.Area;
import org.apache.fop.layout.ForeignObjectArea;
import org.apache.fop.layout.FontState;
import org.apache.fop.apps.FOPException;

import org.apache.fop.dom.svg.*;

import org.apache.fop.dom.svg.SVGArea;
/**
 * class representing svg:svg pseudo flow object.
 */
public class SVG extends FObj implements GraphicsCreator {

	/**
	 * inner class for making SVG objects.
	 */
	public static class Maker extends FObj.Maker {

	/**
	 * make an SVG object.
	 *
	 * @param parent the parent formatting object
	 * @param propertyList the explicit properties of this object
	 *
	 * @return the SVG object
	 */
	public FObj make(FObj parent, PropertyList propertyList)
		throws FOPException {
		return new SVG(parent, propertyList);
	}
	}

	/**
	 * returns the maker for this object.
	 *
	 * @return the maker for SVG objects
	 */
	public static FObj.Maker maker() {
	return new SVG.Maker();
	}

	FontState fs;
	float width;
	float height;

	/**
	 * constructs an SVG object (called by Maker).
	 *
	 * @param parent the parent formatting object
	 * @param propertyList the explicit properties of this object
	 */
	public SVG(FObj parent, PropertyList propertyList) {
	super(parent, propertyList);
	this.name = "svg:svg";
	}

/*	protected void addChild(FONode child) {
		super.addChild(child);
		if(svgArea != null) {
			Status status;
			try {
				if ((status = child.layout(svgArea)).isIncomplete()) {
				}
			} catch(FOPException fope) {
				fope.printStackTrace();
			}
		}
	}*/

	SVGArea svgArea = null;
	public GraphicImpl createGraphic()
	{
		this.width = ((SVGLengthProperty)this.properties.get("width")).getSVGLength().getValue();
		this.height = ((SVGLengthProperty)this.properties.get("height")).getSVGLength().getValue();
		svgArea = new SVGArea(null, width, height);
		svgArea.setStyle(((SVGStyle)this.properties.get("style")).getStyle());
		svgArea.setTransform(((SVGTransform)this.properties.get("transform")).oldgetTransform());
//		svgArea.setId(this.properties.get("id").getString());
		int numChildren = this.children.size();
		for (int i = 0; i < numChildren; i++) {
			FONode fo = (FONode) children.elementAt(i);
			if(fo instanceof GraphicsCreator) {
				GraphicImpl impl = ((GraphicsCreator)fo).createGraphic();
				svgArea.addGraphic(impl);
			} else if(fo instanceof Defs) {
				svgArea.addDefs(((Defs)fo).createDefs());
			}
			Status status;
/*			try {
				if ((status = fo.layout(svgArea)).isIncomplete()) {
//				return null;
				}
			} catch(FOPException fope) {
				fope.printStackTrace();
			}*/
		}
		return svgArea;
	}

	/**
	 * layout this formatting object.
	 *
	 * @param area the area to layout the object into
	 *
	 * @return the status of the layout
	 */
	public Status layout(Area area) throws FOPException {
	
	if (area instanceof SVGArea) {
		((SVGArea) area).addGraphic(createGraphic());
		return new Status(Status.OK);
	}

	if (!(area instanceof ForeignObjectArea)) {
	    // this is an error
	    throw new FOPException("SVG not in fo:instream-foreign-object");
	}

	if (this.marker == BREAK_AFTER) {
		return new Status(Status.OK);
	}

	if (this.marker == START) {
		/* retrieve properties */
		String id = this.properties.get("id").getString();
		String fontFamily = this.properties.get("font-family").getString();
		String fontStyle = this.properties.get("font-style").getString();
		String fontWeight = this.properties.get("font-weight").getString();
		int fontSize = this.properties.get("font-size").getLength().mvalue();
		
		this.fs = new FontState(area.getFontInfo(), fontFamily,
					 fontStyle, fontWeight, fontSize);
		
		this.width = ((SVGLengthProperty)this.properties.get("width")).getSVGLength().getValue();
		this.height = ((SVGLengthProperty)this.properties.get("height")).getSVGLength().getValue();
		
		this.marker = 0;
	}

	/* create an SVG area */
	SVGArea svg = new SVGArea(fs, width, height);
	svg.setStyle(((SVGStyle)this.properties.get("style")).getStyle());
	svg.setTransform(((SVGTransform)this.properties.get("transform")).oldgetTransform());
	svg.start();

	/* add the SVG area to the containing area */
	((ForeignObjectArea)area).setObject(svg);

	/* iterate over the child formatting objects and lay them out
	   into the SVG area */
	int numChildren = this.children.size();
	for (int i = 0; i < numChildren; i++) {
		FONode fo = (FONode) children.elementAt(i);
		Status status;
		if ((status = fo.layout(svg)).isIncomplete()) {
		return status;
		}
	}

	/* finish off the SVG area */
	svg.end();

	/* increase the height of the containing area accordingly */
	area.increaseHeight(svg.getHeight());
//	area.setWidth(svg.getWidth());

	/* return status */
	return new Status(Status.OK);
	}
}