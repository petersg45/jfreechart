/* ===========================================================
 * JFreeChart : a free chart library for the Java(tm) platform
 * ===========================================================
 *
 * (C) Copyright 2000-2021, by David Gilbert and Contributors.
 *
 * Project Info:  http://www.jfree.org/jfreechart/index.html
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * [Oracle and Java are registered trademarks of Oracle and/or its affiliates. 
 * Other names may be trademarks of their respective owners.]
 *
 * ---------------------------
 * ClusteredXYBarRenderer.java
 * ---------------------------
 * (C) Copyright 2003-2020, by Paolo Cova and Contributors.
 *
 * Original Author:  Paolo Cova;
 * Contributor(s):   David Gilbert;
 *                   Christian W. Zuckschwerdt;
 *                   Matthias Rose;
 *
 */

package org.jfree.chart.renderer.xy;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.labels.XYItemLabelGenerator;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.util.Args;
import org.jfree.chart.util.PublicCloneable;
import org.jfree.data.Range;
import org.jfree.data.xy.IntervalXYDataset;
import org.jfree.data.xy.XYDataset;

/**
 * An extension of {@link XYBarRenderer} that displays bars for different
 * series values at the same x next to each other. The assumption here is
 * that for each x (time or else) there is a y value for each series. If
 * this is not the case, there will be spaces between bars for a given x.
 * The example shown here is generated by the
 * {@code ClusteredXYBarRendererDemo1.java} program included in the
 * JFreeChart demo collection:
 * <br><br>
 * <img src="doc-files/ClusteredXYBarRendererSample.png"
 * alt="ClusteredXYBarRendererSample.png">
 * <P>
 * This renderer does not include code to calculate the crosshair point for the
 * plot.
 */
public class ClusteredXYBarRenderer extends XYBarRenderer
        implements Cloneable, PublicCloneable, Serializable {

    /** For serialization. */
    private static final long serialVersionUID = 5864462149177133147L;

    /** Determines whether bar center should be interval start. */
    private boolean centerBarAtStartValue;

    /**
     * Default constructor. Bar margin is set to 0.0.
     */
    public ClusteredXYBarRenderer() {
        this(0.0, false);
    }

    /**
     * Constructs a new XY clustered bar renderer.
     *
     * @param margin  the percentage amount to trim from the width of each bar.
     * @param centerBarAtStartValue  if true, bars will be centered on the
     *         start of the time period.
     */
    public ClusteredXYBarRenderer(double margin,
                                  boolean centerBarAtStartValue) {
        super(margin);
        this.centerBarAtStartValue = centerBarAtStartValue;
    }

    /**
     * Returns the number of passes through the dataset that this renderer
     * requires.  In this case, two passes are required, the first for drawing
     * the shadows (if visible), and the second for drawing the bars.
     *
     * @return {@code 2}.
     */
    @Override
    public int getPassCount() {
        return 2;
    }

    /**
     * Returns the x-value bounds for the specified dataset.
     *
     * @param dataset  the dataset ({@code null} permitted).
     *
     * @return The bounds (possibly {@code null}).
     */
    @Override
    public Range findDomainBounds(XYDataset dataset) {
        if (dataset == null) {
            return null;
        }
        // need to handle cluster centering as a special case
        if (this.centerBarAtStartValue) {
            return findDomainBoundsWithOffset((IntervalXYDataset) dataset);
        } else {
            return super.findDomainBounds(dataset);
        }
    }

    /**
     * Iterates over the items in an {@link IntervalXYDataset} to find
     * the range of x-values including the interval OFFSET so that it centers
     * the interval around the start value.
     *
     * @param dataset  the dataset ({@code null} not permitted).
     *
     * @return The range (possibly {@code null}).
     */
    protected Range findDomainBoundsWithOffset(IntervalXYDataset dataset) {
        Args.nullNotPermitted(dataset, "dataset");
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        int seriesCount = dataset.getSeriesCount();
        double lvalue;
        double uvalue;
        for (int series = 0; series < seriesCount; series++) {
            int itemCount = dataset.getItemCount(series);
            for (int item = 0; item < itemCount; item++) {
                lvalue = dataset.getStartXValue(series, item);
                uvalue = dataset.getEndXValue(series, item);
                double offset = (uvalue - lvalue) / 2.0;
                lvalue = lvalue - offset;
                uvalue = uvalue - offset;
                minimum = Math.min(minimum, lvalue);
                maximum = Math.max(maximum, uvalue);
            }
        }

        if (minimum > maximum) {
            return null;
        } else {
            return new Range(minimum, maximum);
        }
    }

    /**
     * Draws the visual representation of a single data item. This method
     * is mostly copied from the superclass, the change is that in the
     * calculated space for a singe bar we draw bars for each series next to
     * each other. The width of each bar is the available width divided by
     * the number of series. Bars for each series are drawn in order left to
     * right.
     *
     * @param g2  the graphics device.
     * @param state  the renderer state.
     * @param dataArea  the area within which the plot is being drawn.
     * @param info  collects information about the drawing.
     * @param plot  the plot (can be used to obtain standard color
     *              information etc).
     * @param domainAxis  the domain axis.
     * @param rangeAxis  the range axis.
     * @param dataset  the dataset.
     * @param series  the series index.
     * @param item  the item index.
     * @param crosshairState  crosshair information for the plot
     *                        ({@code null} permitted).
     * @param pass  the pass index.
     */
    @Override
    public void drawItem(Graphics2D g2, XYItemRendererState state, 
            Rectangle2D dataArea, PlotRenderingInfo info, XYPlot plot,
            ValueAxis domainAxis, ValueAxis rangeAxis, XYDataset dataset, 
            int series, int item, CrosshairState crosshairState, int pass) {

        if (!getItemVisible(series, item)) {
            return;
        }

        IntervalXYDataset intervalDataset = (IntervalXYDataset) dataset;

        double y0;
        double y1;
        if (getUseYInterval()) {
            y0 = intervalDataset.getStartYValue(series, item);
            y1 = intervalDataset.getEndYValue(series, item);
        } else {
            y0 = getBase();
            y1 = intervalDataset.getYValue(series, item);
        }
        if (Double.isNaN(y0) || Double.isNaN(y1)) {
            return;
        }

        double yy0 = rangeAxis.valueToJava2D(y0, dataArea,
                plot.getRangeAxisEdge());
        double yy1 = rangeAxis.valueToJava2D(y1, dataArea,
                plot.getRangeAxisEdge());

        RectangleEdge xAxisLocation = plot.getDomainAxisEdge();
        double x0 = intervalDataset.getStartXValue(series, item);
        double xx0 = domainAxis.valueToJava2D(x0, dataArea, xAxisLocation);

        double x1 = intervalDataset.getEndXValue(series, item);
        double xx1 = domainAxis.valueToJava2D(x1, dataArea, xAxisLocation);

        double intervalW = xx1 - xx0;  // this may be negative
        double baseX = xx0;
        if (this.centerBarAtStartValue) {
            baseX = baseX - intervalW / 2.0;
        }
        double m = getMargin();
        if (m > 0.0) {
            double cut = intervalW * getMargin();
            intervalW = intervalW - cut;
            baseX = baseX + (cut / 2);
        }

        double intervalH = Math.abs(yy0 - yy1);  // we don't need the sign

        PlotOrientation orientation = plot.getOrientation();

        List<Integer> visibleSeries = new ArrayList<>();
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            if (isSeriesVisible(i)) {
                visibleSeries.add(i);
            }
        }

        int numSeries = visibleSeries.size();
        double seriesBarWidth = intervalW / numSeries;  // may be negative
        int visibleSeriesIndex = visibleSeries.indexOf(series);

        Rectangle2D bar = null;
        if (orientation == PlotOrientation.HORIZONTAL) {
            double barY0 = baseX + (seriesBarWidth * visibleSeriesIndex);
            double barY1 = barY0 + seriesBarWidth;
            double rx = Math.min(yy0, yy1);
            double rw = intervalH;
            double ry = Math.min(barY0, barY1);
            double rh = Math.abs(barY1 - barY0);
            bar = new Rectangle2D.Double(rx, ry, rw, rh);
        } else if (orientation == PlotOrientation.VERTICAL) {
            double barX0 = baseX + (seriesBarWidth * visibleSeriesIndex);
            double barX1 = barX0 + seriesBarWidth;
            double rx = Math.min(barX0, barX1);
            double rw = Math.abs(barX1 - barX0);
            double ry = Math.min(yy0, yy1);
            double rh = intervalH;
            bar = new Rectangle2D.Double(rx, ry, rw, rh);
        } else {
            throw new IllegalStateException();
        }
        boolean positive = (y1 > 0.0);
        boolean inverted = rangeAxis.isInverted();
        RectangleEdge barBase;
        if (orientation == PlotOrientation.HORIZONTAL) {
            if (positive && inverted || !positive && !inverted) {
                barBase = RectangleEdge.RIGHT;
            } else {
                barBase = RectangleEdge.LEFT;
            }
        } else {
            if (positive && !inverted || !positive && inverted) {
                barBase = RectangleEdge.BOTTOM;
            } else {
                barBase = RectangleEdge.TOP;
            }
        }
        if (pass == 0 && getShadowsVisible()) {
            getBarPainter().paintBarShadow(g2, this, series, item, bar, barBase,
                !getUseYInterval());
        }
        if (pass == 1) {
            getBarPainter().paintBar(g2, this, series, item, bar, barBase);

            if (isItemLabelVisible(series, item)) {
                XYItemLabelGenerator generator = getItemLabelGenerator(series,
                        item);
                drawItemLabel(g2, dataset, series, item, plot, generator, bar,
                        y1 < 0.0);
            }

            // add an entity for the item...
            if (info != null) {
                EntityCollection entities
                        = info.getOwner().getEntityCollection();
                if (entities != null) {
                    addEntity(entities, bar, dataset, series, item,
                            bar.getCenterX(), bar.getCenterY());
                }
            }
        }

    }

    /**
     * Tests this renderer for equality with an arbitrary object, returning
     * {@code true} if {@code obj} is a {@code ClusteredXYBarRenderer} with the
     * same settings as this renderer, and {@code false} otherwise.
     *
     * @param obj  the object ({@code null} permitted).
     *
     * @return A boolean.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof ClusteredXYBarRenderer)) {
            return false;
        }
        ClusteredXYBarRenderer that = (ClusteredXYBarRenderer) obj;
        if (this.centerBarAtStartValue != that.centerBarAtStartValue) {
            return false;
        }
        return super.equals(obj);
    }

    /**
     * Returns a clone of the renderer.
     *
     * @return A clone.
     *
     * @throws CloneNotSupportedException  if the renderer cannot be cloned.
     */
    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

}
