package org.vufind.index;
/**
 * Dewey Call number indexing routines.
 *
 * Copyright (C) Villanova University 2017.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.marc4j.marc.Record;
import org.marc4j.marc.VariableField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Subfield;
import org.solrmarc.callnum.DeweyCallNumber;
import org.solrmarc.index.SolrIndexer;
import org.solrmarc.tools.CallNumUtils;

/**
 * Dewey Call number indexing routines.
 */
public class DeweyCallNumberTools
{
    /**
     * Extract a numeric portion of the Dewey decimal call number
     *
     * Can return null
     *
     * @param record current MARC record
     * @param fieldSpec which MARC fields / subfields need to be analyzed
     * @param precisionStr a decimal number (represented in string format) showing the
     *  desired precision of the returned number; i.e. 100 to round to nearest hundred,
     *  10 to round to nearest ten, 0.1 to round to nearest tenth, etc.
     * @return Set containing requested numeric portions of Dewey decimal call numbers
     */
    public Set<String> getDeweyNumber(Record record, String fieldSpec, String precisionStr) {
        // Initialize our return value:
        Set<String> result = new LinkedHashSet<String>();

        // Precision comes in as a string, but we need to convert it to a float:
        float precision = Float.parseFloat(precisionStr);

        // Loop through the specified MARC fields:
        Set<String> input = SolrIndexer.instance().getFieldList(record, fieldSpec);
        for (String current: input) {
            DeweyCallNumber callNum = new DeweyCallNumber(current);
            if (callNum.isValid()) {
                // Convert the numeric portion of the call number into a float:
                float currentVal = Float.parseFloat(callNum.getClassification());
                
                // Round the call number value to the specified precision:
                Float finalVal = new Float(Math.floor(currentVal / precision) * precision);
                
                // Convert the rounded value back to a string (with leading zeros) and save it:
                // TODO: Provide different conversion to remove CallNumUtils dependency
                result.add(CallNumUtils.normalizeFloat(finalVal.toString(), 3, -1));
            }
        }

        // If we found no call number matches, return null; otherwise, return our results:
        if (result.isEmpty())
            return null;
        return result;
    }

    /**
     * Normalize Dewey numbers for searching purposes (uppercase/stripped spaces)
     *
     * Can return null
     *
     * @param record current MARC record
     * @param fieldSpec which MARC fields / subfields need to be analyzed
     * @return Set containing normalized Dewey numbers extracted from specified fields.
     */
    public Set<String> getDeweySearchable(Record record, String fieldSpec) {
        // Initialize our return value:
        Set<String> result = new LinkedHashSet<String>();

        // Loop through the specified MARC fields:
        Set<String> input = SolrIndexer.instance().getFieldList(record, fieldSpec);
        Iterator<String> iter = input.iterator();
        while (iter.hasNext()) {
            // Get the current string to work on:
            String current = iter.next();

            // Add valid strings to the set, normalizing them to be all uppercase
            // and free from whitespace.
            DeweyCallNumber callNum = new DeweyCallNumber(current);
            if (callNum.isValid()) {
                result.add(callNum.toString().toUpperCase().replaceAll(" ", ""));
            }
        }

        // If we found no call numbers, return null; otherwise, return our results:
        if (result.isEmpty())
            return null;
        return result;
    }

    /**
     * Normalize Dewey numbers for sorting purposes (use only the first valid number!)
     *
     * Can return null
     *
     * @param record current MARC record
     * @param fieldSpec which MARC fields / subfields need to be analyzed
     * @return String containing the first valid Dewey number encountered, normalized
     *         for sorting purposes.
     */
    public String getDeweySortable(Record record, String fieldSpec) {
        // Loop through the specified MARC fields:
        Set<String> input = SolrIndexer.instance().getFieldList(record, fieldSpec);
        Iterator<String> iter = input.iterator();
        while (iter.hasNext()) {
            // Get the current string to work on:
            String current = iter.next();

            // If this is a valid Dewey number, return the sortable shelf key:
            DeweyCallNumber callNum = new DeweyCallNumber(current);
            if (callNum.isValid()) {
                return callNum.getShelfKey();
            }
        }

        // If we made it this far, we didn't find a valid sortable Dewey number:
        return null;
    }

    /**
     * Get sort key for first Dewey call number, identified by call type.
     * 
     * <p>{@code fieldSpec} is of form {@literal 098abc:099ab}, does not accept subfield ranges.
     *
     *
     * @param record  current MARC record
     * @param fieldSpec  which MARC fields / subfields need to be analyzed
     * @param callTypeSf  subfield containing call number type, single character only
     * @param callType  literal call number code
     * @return sort key for first identified Dewey call number
     */
    public static String getDeweySortableByType(
            Record record, String fieldSpec, String callTypeSf, String callType) {
        String sortKey = null;
        for (String tag : fieldSpec.split(":")) {
            // Check to ensure tag length is at least 3 characters
            if (tag.length() < 3) {
                //TODO: Should this go to a log? Better message for a bad tag in a field spec?
                System.err.println("Invalid tag specified: " + tag);
                continue;
            }
            String dfTag = tag.substring(0, 3);
            String sfSpec = null;
            if (tag.length() > 3) {
                    sfSpec = tag.substring(3);
            }

            // do all fields for this tag
            for (VariableField vf : record.getVariableFields(dfTag)) {
                // Assume tag represents a DataField
                DataField df = (DataField) vf;
                boolean callTypeMatch = false;
                
                // Assume call type subfield could repeat
                for (Subfield typeSf : df.getSubfields(callTypeSf)) {
                    if (callTypeSf.indexOf(typeSf.getCode()) != -1 && typeSf.getData().equals(callType)) {
                        callTypeMatch = true;
                    }
                }
                // take the first call number coded as Dewey
                if (callTypeMatch) {
                    sortKey = new DeweyCallNumber(df.getSubfieldsAsString(sfSpec)).getShelfKey();
                    break;
                }
            } // end loop over variable fields
        } // end loop over fieldSpec
        return sortKey;
    }

    
    /**
     * Normalize Dewey numbers for AlphaBrowse sorting purposes (use all numbers!)
     *
     * Can return null
     *
     * @param record current MARC record
     * @param fieldSpec which MARC fields / subfields need to be analyzed
     * @return List containing normalized Dewey numbers extracted from specified fields.
     */
    public List<String> getDeweySortables(Record record, String fieldSpec) {
        // Initialize our return value:
        List<String> result = new LinkedList<String>();

        // Loop through the specified MARC fields:
        Set<String> input = SolrIndexer.instance().getFieldList(record, fieldSpec);
        Iterator<String> iter = input.iterator();
        while (iter.hasNext()) {
            // Get the current string to work on:
            String current = iter.next();

            // gather all sort keys, even if number is not valid
            DeweyCallNumber callNum = new DeweyCallNumber(current);
            result.add(callNum.getShelfKey());
        }

        // If we found no call numbers, return null; otherwise, return our results:
        if (result.isEmpty())
            return null;
        return result;
    }
}