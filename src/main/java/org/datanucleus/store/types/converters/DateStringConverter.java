/**********************************************************************
Copyright (c) 2009 Andy Jefferson and others. All rights reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Contributors:
    ...
**********************************************************************/
package org.datanucleus.store.types.converters;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.datanucleus.exceptions.NucleusDataStoreException;
import org.datanucleus.util.Localiser;

/**
 * Class to handle the conversion between java.util.Date and a String form.
 * The String form follows the format "EEE MMM dd HH:mm:ss zzz yyyy". That is, milliseconds are not retained currently.
 */
public class DateStringConverter implements TypeConverter<Date, String>
{
    private static final long serialVersionUID = 4638239842151376340L;
    private static final ThreadLocal<FormatterInfo> formatterThreadInfo = new ThreadLocal<FormatterInfo>()
    {
        protected FormatterInfo initialValue()
        {
            return new FormatterInfo();
        }
    };

    static class FormatterInfo
    {
        SimpleDateFormat formatter;
    }

    private DateFormat getFormatter()
    {
        FormatterInfo formatInfo = formatterThreadInfo.get();
        if (formatInfo.formatter == null)
        {
            // TODO Handle millisecs
            formatInfo.formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }
        return formatInfo.formatter;
    }

    public Date toMemberType(String str)
    {
        if (str == null)
        {
            return null;
        }

        try
        {
            return getFormatter().parse(str);
        }
        catch (ParseException pe)
        {
            throw new NucleusDataStoreException(Localiser.msg("016002", str, Date.class.getName()), pe);
        }
    }

    public String toDatastoreType(Date date)
    {
        return date != null ? getFormatter().format(date) : null;
    }
}
