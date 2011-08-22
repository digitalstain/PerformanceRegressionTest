/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.bench.chart;

import java.awt.Color;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer3D;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.ui.TextAnchor;
import org.neo4j.bench.cases.mixedload.Stats;

public class GenerateOpsPerSecChart
{
    private static final int TESTS_TO_DRAW = 7;
    public static final String OPS_PER_SECOND_FILE_ARG = "ops-per-sec-file";
    public static final String CHART_FILE_ARG = "chart-file";

    private final String inputFilename;
    private final String outputFilename;
    private boolean alarm;
    private final SortedSet<Stats> data;
    private Set<Stats> dataToDraw;
    private final double threshold;
    private double maxAvg;

    public GenerateOpsPerSecChart( String inputFilename, String outputFilename,
            double threshold )
    {
        this.inputFilename = inputFilename;
        this.outputFilename = outputFilename;
        this.threshold = threshold;
        data = loadOpsPerSecond( this.inputFilename );
    }

    public boolean process() throws Exception
    {
        // Take the latest
        if ( data.size() > TESTS_TO_DRAW )
        {
            Iterator<Stats> it = data.iterator();
            int i = 0;
            while ( data.size() - i++ > TESTS_TO_DRAW )
            {
                it.next();
            }
            dataToDraw = data.tailSet( it.next() );
        }
        else
        {
            dataToDraw = data;
        }
        alarm = detectDegradation( threshold ) != null;
        generateChart();
        return alarm;
    }

    private Stats detectDegradation( double threshold )
    {
        Stats latestRun = data.last();
        System.out.println( "Latest run test is " + latestRun );
        for ( Stats previous : data.headSet( latestRun ) )
        {
            double previousReads = previous.getAvgReadsPerSec();
            double previousWrites = previous.getAvgWritePerSec();
            if ( previousReads * ( 1 + threshold ) > latestRun.getAvgReadsPerSec()
                    || previousWrites * ( 1 + threshold ) > latestRun.getAvgWritePerSec() )
            {
                return previous;
            }
        }
        return null;
    }

    private void generateChart() throws Exception
    {
        DefaultCategoryDataset dataset = generateDataset();

        BarRenderer3D barRenderer = new BarRenderer3D();
        barRenderer.setBaseItemLabelsVisible( true );
        barRenderer.setBaseItemLabelGenerator( new StandardCategoryItemLabelGenerator(
                "{2}", new DecimalFormat( "###.#" ) ) );
        barRenderer.setBasePositiveItemLabelPosition( new ItemLabelPosition(
                ItemLabelAnchor.OUTSIDE12, TextAnchor.TOP_CENTER ) );
        barRenderer.setItemMargin( 0.06 );

        CategoryAxis catAxis = new CategoryAxis( "Bench Case" );

        CategoryPlot basePlot = new CategoryPlot( dataset, catAxis,
                new NumberAxis(
                "Operations Per Sec" ), barRenderer );
        basePlot.setOrientation( PlotOrientation.VERTICAL );
        basePlot.setDataset( dataset );
        basePlot.getRangeAxis().setLowerBound( 0 );
        basePlot.getRangeAxis().setUpperBound( maxAvg );

        JFreeChart chart = new JFreeChart( "Performance Chart", basePlot );

        Dimension dimensions = new Dimension( 1600, 900 );
        File chartFile = new File( outputFilename );
        if ( alarm )
        {
            chart.setBackgroundPaint( Color.RED );
        }
        ChartUtilities.saveChartAsPNG( chartFile, chart,
                (int) dimensions.getWidth(), (int) dimensions.getHeight() );
    }

    private DefaultCategoryDataset generateDataset()
    {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        double currentMax = Double.MIN_VALUE;

        for ( Stats key : dataToDraw )
        {
            dataset.addValue( key.getAvgReadsPerSec(), "reads", key.getName() );
            if ( key.getAvgReadsPerSec() > currentMax )
            {
                currentMax = key.getAvgReadsPerSec();
            }
            dataset.addValue( key.getAvgWritePerSec(), "writes",
                    key.getName() );
            dataset.addValue( key.getPeakReadsPerSec(), "peak reads",
                    key.getName() );
            dataset.addValue( key.getPeakWritesPerSec(), "peak writes",
                    key.getName() );
            dataset.addValue( key.getSustainedReadsPerSec(), "sust reads",
                    key.getName() );
            dataset.addValue( key.getSustainedWritesPerSec(), "sust writes",
                    key.getName() );
        }
        this.maxAvg = currentMax;
        return dataset;
    }

    /**
     * Opens the operations per second file, reads in the contents and creates a
     * SortedSet of the therein stored Stats.
     */
    public static SortedSet<Stats> loadOpsPerSecond( String fileName )
    {
        File dataFile = new File( fileName );
        if ( !dataFile.exists() )
        {
            return null;
        }
        BufferedReader reader = null;
        SortedSet<Stats> result = new TreeSet<Stats>();
        Stats currentStat;
        try
        {
            reader = new BufferedReader( new FileReader( dataFile ) );
            String line; // The current line
            while ( ( line = reader.readLine() ) != null )
            {
                currentStat = Stats.parse( line );
                if ( currentStat != null )
                {
                    result.add( currentStat );
                }
            }
        }
        catch ( IOException e )
        {
            // This should not happen as we check above
            e.printStackTrace();
            return null;
        }
        finally
        {
            if ( reader != null )
            {
                try
                {
                    reader.close();
                }
                catch ( IOException e )
                {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }
}
