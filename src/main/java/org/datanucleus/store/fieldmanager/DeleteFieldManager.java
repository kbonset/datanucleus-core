/**********************************************************************
Copyright (c) 2006 Andy Jefferson and others. All rights reserved.
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
2011 Andy Jefferson - add null if bidir and not dependent functionality
    ...
**********************************************************************/
package org.datanucleus.store.fieldmanager;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.datanucleus.ExecutionContext;
import org.datanucleus.api.ApiAdapter;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.RelationType;
import org.datanucleus.state.ObjectProvider;

/**
 * Field manager that deletes all "dependent" PC objects referenced from the source object.
 * Effectively provides "delete-dependent".
 */
public class DeleteFieldManager extends AbstractFieldManager
{
    /** ObjectProvider for the owning object. */
    private final ObjectProvider op;

    private boolean manageRelationships = false;

    /**
     * Constructor.
     * @param op The ObjectProvider for the object.
     */
    public DeleteFieldManager(ObjectProvider op)
    {
        this(op, false);
    }

    /**
     * Constructor.
     * @param op The ObjectProvider for the object.
     * @param manageRelationships Whether to make an attempt to manage relationships when bidir fields are affected by this deletion (RDBMS typically doesnt need this)
     */
    public DeleteFieldManager(ObjectProvider op, boolean manageRelationships)
    {
        this.op = op;
        this.manageRelationships = manageRelationships;
    }

    /**
     * Utility method to process the passed persistable object.
     * @param pc The PC object
     */
    protected void processPersistable(Object pc)
    {
        ObjectProvider pcOP = op.getExecutionContext().findObjectProvider(pc);
        if (pcOP != null)
        {
            if (pcOP.isDeleting() || pcOP.becomingDeleted())
            {
                // Already becoming deleted so jump out
                return;
            }
        }

        // Delete it
        op.getExecutionContext().deleteObjectInternal(pc);
    }

    /**
     * Method to store an object field.
     * @param fieldNumber Number of the field (absolute)
     * @param value Value of the field
     */
    public void storeObjectField(int fieldNumber, Object value)
    {
        if (value != null)
        {
            AbstractMemberMetaData mmd = op.getClassMetaData().getMetaDataForManagedMemberAtAbsolutePosition(fieldNumber);
            ExecutionContext ec = op.getExecutionContext();
            RelationType relationType = mmd.getRelationType(ec.getClassLoaderResolver());
            if (RelationType.isRelationSingleValued(relationType))
            {
                // Process PC fields
                if (mmd.isDependent())
                {
                    processPersistable(value);
                }
                else if (manageRelationships && RelationType.isBidirectional(relationType) && !mmd.isEmbedded())
                {
                    ObjectProvider valueOP = ec.findObjectProvider(value);
                    if (valueOP != null && !valueOP.getLifecycleState().isDeleted() && !valueOP.isDeleting())
                    {
                        AbstractMemberMetaData relMmd = mmd.getRelatedMemberMetaData(ec.getClassLoaderResolver())[0];
                        if (relationType == RelationType.ONE_TO_ONE_BI)
                        {
                            valueOP.replaceFieldMakeDirty(relMmd.getAbsoluteFieldNumber(), null);
                            valueOP.flush();
                        }
                        else if (relationType == RelationType.MANY_TO_ONE_BI)
                        {
                            // Make sure field at other side is loaded, and remove from any Collection
                            valueOP.loadField(relMmd.getAbsoluteFieldNumber());
                            Object relValue = valueOP.provideField(relMmd.getAbsoluteFieldNumber());
                            if (relValue != null)
                            {
                                if (relValue instanceof Collection)
                                {
                                    ((Collection)relValue).remove(op.getObject());
                                }
                            }
                        }
                    }
                }
            }
            else if (RelationType.isRelationMultiValued(relationType))
            {
                ApiAdapter api = ec.getApiAdapter();
                if (value instanceof Collection)
                {
                    // Process all elements of the Collection that are PC
                    boolean dependent = mmd.getCollection().isDependentElement();
                    if (mmd.isCascadeRemoveOrphans())
                    {
                        dependent = true;
                    }
                    if (dependent)
                    {
                        // Process any elements that are persistable
                        Collection coll = (Collection)value;
                        Iterator iter = coll.iterator();
                        while (iter.hasNext())
                        {
                            Object element = iter.next();
                            if (api.isPersistable(element))
                            {
                                processPersistable(element);
                            }
                        }
                    }
                    else if (manageRelationships && RelationType.isBidirectional(relationType) && !mmd.isEmbedded() && !mmd.getCollection().isEmbeddedElement())
                    {
                        if (relationType == RelationType.ONE_TO_MANY_BI)
                        {
                            Collection coll = (Collection)value;
                            Iterator iter = coll.iterator();
                            while (iter.hasNext())
                            {
                                Object element = iter.next();
                                if (api.isPersistable(element))
                                {
                                    ObjectProvider elementOP = ec.findObjectProvider(element);
                                    if (elementOP != null && !elementOP.getLifecycleState().isDeleted() && !elementOP.isDeleting())
                                    {
                                        AbstractMemberMetaData relMmd = mmd.getRelatedMemberMetaData(ec.getClassLoaderResolver())[0];
                                        elementOP.replaceFieldMakeDirty(relMmd.getAbsoluteFieldNumber(), null);
                                        elementOP.flush();
                                    }
                                }
                            }
                        }
                    }
                }
                else if (value instanceof Map)
                {
                    // Process all keys, values of the Map that are PC
                    Map map = (Map)value;
                    if (mmd.hasMap() && mmd.getMap().isDependentKey())
                    {
                        // Process any keys that are persistable
                        Set keys = map.keySet();
                        Iterator iter = keys.iterator();
                        while (iter.hasNext())
                        {
                            Object mapKey = iter.next();
                            if (api.isPersistable(mapKey))
                            {
                                processPersistable(mapKey);
                            }
                        }
                    }
                    if (mmd.hasMap() && mmd.getMap().isDependentValue())
                    {
                        // Process any values that are persistable
                        Collection values = map.values();
                        Iterator iter = values.iterator();
                        while (iter.hasNext())
                        {
                            Object mapValue = iter.next();
                            if (api.isPersistable(mapValue))
                            {
                                processPersistable(mapValue);
                            }
                        }
                    }
                    // TODO Handle nulling of bidirs
                }
                else if (value instanceof Object[])
                {
                    // Process all array elements that are PC
                    if (mmd.hasArray() && mmd.getArray().isDependentElement())
                    {
                        // Process any array elements that are persistable
                        for (int i=0;i<Array.getLength(value);i++)
                        {
                            Object element = Array.get(value, i);
                            if (api.isPersistable(element))
                            {
                                processPersistable(element);
                            }
                        }
                    }
                    // TODO Handle nulling of bidirs
                }
                else
                {
                    // Primitive, or primitive array, or some unsupported container type
                }
            }
        }
    }

    public void storeBooleanField(int fieldNumber, boolean value)
    {
        // Do nothing
    }

    public void storeByteField(int fieldNumber, byte value)
    {
        // Do nothing
    }

    public void storeCharField(int fieldNumber, char value)
    {
        // Do nothing
    }

    public void storeDoubleField(int fieldNumber, double value)
    {
        // Do nothing
    }

    public void storeFloatField(int fieldNumber, float value)
    {
        // Do nothing
    }

    public void storeIntField(int fieldNumber, int value)
    {
        // Do nothing
    }

    public void storeLongField(int fieldNumber, long value)
    {
        // Do nothing
    }

    public void storeShortField(int fieldNumber, short value)
    {
        // Do nothing
    }

    public void storeStringField(int fieldNumber, String value)
    {
        // Do nothing
    }
}