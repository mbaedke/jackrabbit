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
package org.apache.jackrabbit.spi.commons.nodetype;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map;

import javax.jcr.PropertyType;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.QItemDefinition;
import org.apache.jackrabbit.spi.QNodeDefinition;
import org.apache.jackrabbit.spi.QNodeTypeDefinition;
import org.apache.jackrabbit.spi.QPropertyDefinition;
import org.apache.jackrabbit.spi.QValueConstraint;

/**
 * A <code>NodeTypeDefDiff</code> represents the result of the comparison of
 * two node type definitions.
 * <p/>
 * The result of the comparison can be categorized as one of the following types:
 * <p/>
 * <b><code>NONE</code></b> inidcates that there is no modification at all.
 * <p/>
 * A <b><code>TRIVIAL</code></b> modification has no impact on the consistency
 * of existing content and does not affect existing/assigned definition id's.
 * The following modifications are considered <code>TRIVIAL</code>:
 * <ul>
 * <li>changing node type <code>orderableChildNodes</code> flag
 * <li>changing node type <code>primaryItemName</code> value
 * <li>adding non-<code>mandatory</code> property/child node
 * <li>changing property/child node <code>protected</code> flag
 * <li>changing property/child node <code>onParentVersion</code> value
 * <li>changing property/child node <code>mandatory</code> flag to <code>false</code>
 * <li>changing property/child node <code>autoCreated</code> flag
 * <li>changing child node <code>defaultPrimaryType</code>
 * <li>changing child node <code>sameNameSiblings</code> flag to <code>true</code>
 * <li>weaken property <code>valueConstraints</code> (e.g. by removing completely
 * or by adding to existing or by making a single constraint less restrictive)
 * <li>changing property <code>defaultValues</code>
 * </ul>
 * <p/>
 * A <b><code>MINOR</code></b> modification has no impact on the consistency
 * of existing content but <i>does</i> affect existing/assigned definition id's.
 * The following modifications are considered <code>MINOR</code>:
 * <ul>
 * <li>changing specific property/child node <code>name</code> to <code>*</code>
 * <li>weaken child node <code>requiredPrimaryTypes</code> (e.g. by removing)
 * <li>changing specific property <code>requiredType</code> to <code>undefined</code>
 * <li>changing property <code>multiple</code> flag to <code>true</code>
 * </ul>
 * <p/>
 * A <b><code>MAJOR</code></b> modification <i>affects</i> the consistency of
 * existing content and <i>does</i> change existing/assigned definition id's.
 * All modifications that are neither <b><code>TRIVIAL</code></b> nor
 * <b><code>MINOR</code></b> are considered <b><code>MAJOR</code></b>.
 *
 * @see #getType()
 */
public class NodeTypeDefDiff {

    /**
     * no modification
     */
    public static final int NONE = 0;
    /**
     * trivial modification: does neither affect consistency of existing content
     * nor does it change existing/assigned definition id's
     */
    public static final int TRIVIAL = 1;
    /**
     * minor modification: does not affect consistency of existing content but
     * <i>does</i> change existing/assigned definition id's
     */
    public static final int MINOR = 2;
    /**
     * major modification: <i>does</i> affect consistency of existing content
     * and <i>does</i> change existing/assigned definition id's
     */
    public static final int MAJOR = 3;

    private final QNodeTypeDefinition oldDef;
    private final QNodeTypeDefinition newDef;
    private int type;

    private final List<PropDefDiff> propDefDiffs = new ArrayList<PropDefDiff>();
    private final List<ChildNodeDefDiff> childNodeDefDiffs = new ArrayList<ChildNodeDefDiff>();

    /**
     * Constructor
     * @param oldDef old definition
     * @param newDef new definition
     */
    private NodeTypeDefDiff(QNodeTypeDefinition oldDef, QNodeTypeDefinition newDef) {
        this.oldDef = oldDef;
        this.newDef = newDef;
        init();
    }

    /**
     *
     */
    private void init() {
        if (oldDef.equals(newDef)) {
            // definitions are identical
            type = NONE;
        } else {
            // definitions are not identical, determine type of modification

            // assume TRIVIAL change by default
            type = TRIVIAL;

            // check supertypes
            int tmpType = supertypesDiff();
            if (tmpType > type) {
                type = tmpType;
            }

            // check mixin flag (MAJOR modification)
            tmpType = mixinFlagDiff();
            if (tmpType > type) {
                type = tmpType;
            }

            // no need to check orderableChildNodes flag (TRIVIAL modification)

            // check property definitions
            tmpType = buildPropDefDiffs();
            if (tmpType > type) {
                type = tmpType;
            }

            // check child node definitions
            tmpType = buildChildNodeDefDiffs();
            if (tmpType > type) {
                type = tmpType;
            }
        }
    }

    /**
     * @param oldDef old definition
     * @param newDef new definition
     * @return the diff
     */
    public static NodeTypeDefDiff create(QNodeTypeDefinition oldDef, QNodeTypeDefinition newDef) {
        if (oldDef == null || newDef == null) {
            throw new IllegalArgumentException("arguments can not be null");
        }
        if (!oldDef.getName().equals(newDef.getName())) {
            throw new IllegalArgumentException("at least node type names must be matching");
        }
        return new NodeTypeDefDiff(oldDef, newDef);
    }

    /**
     * @return <code>true</code> if modified
     */
    public boolean isModified() {
        return type != NONE;
    }

    /**
     * @return <code>true</code> if trivial
     */
    public boolean isTrivial() {
        return type == TRIVIAL;
    }

    /**
     * @return <code>true</code> if minor
     */
    public boolean isMinor() {
        return type == MINOR;
    }

    /**
     * @return <code>true</code> if major
     */
    public boolean isMajor() {
        return type == MAJOR;
    }

    /**
     * Returns the type of modification as expressed by the following constants:
     * <ul>
     * <li><b><code>NONE</code></b>: no modification at all
     * <li><b><code>TRIVIAL</code></b>: does neither affect consistency of
     * existing content nor does it change existing/assigned definition id's
     * <li><b><code>MINOR</code></b>: does not affect consistency of existing
     * content but <i>does</i> change existing/assigned definition id's
     * <li><b><code>MAJOR</code></b>: <i>does</i> affect consistency of existing
     * content and <i>does</i> change existing/assigned definition id's
     * </ul>
     *
     * @return the type of modification
     */
    public int getType() {
        return type;
    }

    /**
     * @return <code>true</code> if mixin diff
     */
    public int mixinFlagDiff() {
        return oldDef.isMixin() != newDef.isMixin() ? MAJOR : NONE;
    }

    /**
     * @return <code>true</code> if supertypes diff
     */
    public int supertypesDiff() {
        return !Arrays.equals(oldDef.getSupertypes(), newDef.getSupertypes()) ? MAJOR : NONE;
    }

    /**
     * @return diff type
     */
    private int buildPropDefDiffs() {
        /**
         * propDefId determinants: declaringNodeType, name, requiredType, multiple
         * todo: try also to match entries with modified id's
         */

        int maxType = NONE;
        QPropertyDefinition[] pda1 = oldDef.getPropertyDefs();
        Map<Name, QPropertyDefinition> defs1 = new HashMap<Name, QPropertyDefinition>();
        for (QPropertyDefinition aPda1 : pda1) {
            defs1.put(aPda1.getName(), aPda1);
        }

        QPropertyDefinition[] pda2 = newDef.getPropertyDefs();
        Map<Name, QPropertyDefinition> defs2 = new HashMap<Name, QPropertyDefinition>();
        for (QPropertyDefinition aPda2 : pda2) {
            defs2.put(aPda2.getName(), aPda2);
        }

        /**
         * walk through defs1 and process all entries found in
         * both defs1 & defs2 and those found only in defs1
         */
        Iterator iter = defs1.keySet().iterator();
        while (iter.hasNext()) {
            Name name = (Name) iter.next();
            QPropertyDefinition def1 = defs1.get(name);
            QPropertyDefinition def2 = defs2.get(name);
            PropDefDiff diff = new PropDefDiff(def1, def2);
            if (diff.getType() > maxType) {
                maxType = diff.getType();
            }
            propDefDiffs.add(diff);
            defs2.remove(name);
        }

        /**
         * defs2 by now only contains entries found in defs2 only;
         * walk through defs2 and process all remaining entries
         */
        iter = defs2.keySet().iterator();
        while (iter.hasNext()) {
            Name name = (Name) iter.next();
            QPropertyDefinition def = defs2.get(name);
            PropDefDiff diff = new PropDefDiff(null, def);
            if (diff.getType() > maxType) {
                maxType = diff.getType();
            }
            propDefDiffs.add(diff);
        }

        return maxType;
    }

    /**
     * @return diff type
     */
    private int buildChildNodeDefDiffs() {
        /**
         * nodeDefId determinants: declaringNodeType, name, requiredPrimaryTypes
         * todo: try also to match entries with modified id's
         */

        int maxType = NONE;
        QNodeDefinition[] cnda1 = oldDef.getChildNodeDefs();
        Map<Name, QNodeDefinition> defs1 = new HashMap<Name, QNodeDefinition>();
        for (QNodeDefinition aCnda1 : cnda1) {
            defs1.put(aCnda1.getName(), aCnda1);
        }

        QNodeDefinition[] cnda2 = newDef.getChildNodeDefs();
        Map<Name, QNodeDefinition> defs2 = new HashMap<Name, QNodeDefinition>();
        for (QNodeDefinition aCnda2 : cnda2) {
            defs2.put(aCnda2.getName(), aCnda2);
        }

        /**
         * walk through defs1 and process all entries found in
         * both defs1 & defs2 and those found only in defs1
         */
        Iterator iter = defs1.keySet().iterator();
        while (iter.hasNext()) {
            Name name = (Name) iter.next();
            QNodeDefinition def1 = defs1.get(name);
            QNodeDefinition def2 = defs2.get(name);
            ChildNodeDefDiff diff = new ChildNodeDefDiff(def1, def2);
            if (diff.getType() > maxType) {
                maxType = diff.getType();
            }
            childNodeDefDiffs.add(diff);
            defs2.remove(name);
        }

        /**
         * defs2 by now only contains entries found in defs2 only;
         * walk through defs2 and process all remaining entries
         */
        iter = defs2.keySet().iterator();
        while (iter.hasNext()) {
            Name name = (Name) iter.next();
            QNodeDefinition def = defs2.get(name);
            ChildNodeDefDiff diff = new ChildNodeDefDiff(null, def);
            if (diff.getType() > maxType) {
                maxType = diff.getType();
            }
            childNodeDefDiffs.add(diff);
        }

        return maxType;
    }

    public String toString() {
        String result = getClass().getName() + "[\n\tnodeTypeName="
                + oldDef.getName();

        result += ",\n\tmixinFlagDiff=" + modificationTypeToString(mixinFlagDiff());
        result += ",\n\tsupertypesDiff=" + modificationTypeToString(supertypesDiff());

        result += ",\n\tpropertyDifferences=[\n";
        result += toString(propDefDiffs);
        result += "\t]";

        result += ",\n\tchildNodeDifferences=[\n";
        result += toString(childNodeDefDiffs);
        result += "\t]\n";
        result += "]\n";

        return result;
    }

    private String toString(List<? extends ChildItemDefDiff> childItemDefDiffs) {
        String result = "";
        for (Iterator iter = childItemDefDiffs.iterator(); iter.hasNext();) {
            ChildItemDefDiff propDefDiff = (ChildItemDefDiff) iter.next();
            result += "\t\t" + propDefDiff;
            if (iter.hasNext()) {
                result += ",";
            }
            result += "\n";
        }
        return result;
    }

    private String modificationTypeToString(int modifcationType) {
        String typeString = "unknown";
        switch (modifcationType) {
            case NONE:
                typeString = "NONE";
                break;
            case TRIVIAL:
                typeString = "TRIVIAL";
                break;
            case MINOR:
                typeString = "MINOR";
                break;
            case MAJOR:
                typeString = "MAJOR";
                break;
        }
        return typeString;
    }


    //--------------------------------------------------------< inner classes >

    abstract class ChildItemDefDiff {
        protected final QItemDefinition oldDef;
        protected final QItemDefinition newDef;
        protected int type;

        ChildItemDefDiff(QItemDefinition oldDef, QItemDefinition newDef) {
            this.oldDef = oldDef;
            this.newDef = newDef;
            init();
        }

        protected void init() {
            // determine type of modification
            if (isAdded()) {
                if (!newDef.isMandatory()) {
                    // adding a non-mandatory child item is a TRIVIAL change
                    type = TRIVIAL;
                } else {
                    // adding a mandatory child item is a MAJOR change
                    type = MAJOR;
                }
            } else if (isRemoved()) {
                // removing a child item is a MAJOR change
                type = MAJOR;
            } else {
                /**
                 * neither added nor removed => has to be either identical
                 * or modified
                 */
                if (oldDef.equals(newDef)) {
                    // identical
                    type = NONE;
                } else {
                    // modified
                    if (oldDef.isMandatory() != newDef.isMandatory()
                            && newDef.isMandatory()) {
                        // making a child item mandatory is a MAJOR change
                        type = MAJOR;
                    } else {
                        if (!oldDef.definesResidual()
                                && newDef.definesResidual()) {
                            // just making a child item residual is a MINOR change
                            type = MINOR;
                        } else {
                            if (!oldDef.getName().equals(newDef.getName())) {
                                // changing the name of a child item is a MAJOR change
                                type = MAJOR;
                            } else {
                                // all other changes are TRIVIAL
                                type = TRIVIAL;
                            }
                        }
                    }
                }
            }
        }

        public int getType() {
            return type;
        }

        public boolean isAdded() {
            return oldDef == null && newDef != null;
        }

        public boolean isRemoved() {
            return oldDef != null && newDef == null;
        }

        public boolean isModified() {
            return oldDef != null && newDef != null
                    && !oldDef.equals(newDef);
        }

        public String toString() {
            String typeString = modificationTypeToString(getType());

            String operationString;
            if (isAdded()) {
                operationString = "ADDED";
            } else if (isModified()) {
                operationString = "MODIFIED";
            } else if (isRemoved()) {
                operationString = "REMOVED";
            } else {
                operationString = "NONE";
            }

            QItemDefinition itemDefinition = (oldDef != null) ? oldDef : newDef;

            return getClass().getName() + "[itemName="
                    + itemDefinition.getName() + ", type=" + typeString
                    + ", operation=" + operationString + "]";
        }

    }

    public class PropDefDiff extends ChildItemDefDiff {

        PropDefDiff(QPropertyDefinition oldDef, QPropertyDefinition newDef) {
            super(oldDef, newDef);
        }

        public QPropertyDefinition getOldDef() {
            return (QPropertyDefinition) oldDef;
        }

        public QPropertyDefinition getNewDef() {
            return (QPropertyDefinition) newDef;
        }

        protected void init() {
            super.init();
            /**
             * only need to do comparison if base class implementation
             * detected a non-MAJOR modification (i.e. TRIVIAL or MINOR);
             * no need to check for additions or removals as this is already
             * handled in base class implementation.
             */
            if (isModified() && type != NONE && type != MAJOR) {
                /**
                 * check if valueConstraints were made more restrictive
                 * (constraints are ORed)
                 */
                QValueConstraint[] vca1 = getOldDef().getValueConstraints();
                Set<String> set1 = new HashSet<String>();
                for (QValueConstraint aVca1 : vca1) {
                    set1.add(aVca1.getString());
                }
                QValueConstraint[] vca2 = getNewDef().getValueConstraints();
                Set<String> set2 = new HashSet<String>();
                for (QValueConstraint aVca2 : vca2) {
                    set2.add(aVca2.getString());
                }

                if (set1.isEmpty() && !set2.isEmpty()) {
                    // added constraint where there was no constraint (MAJOR change)
                    type = MAJOR;
                } else if (!set2.containsAll(set1) && !set2.isEmpty()) {
                    // removed existing constraint (MAJOR change)
                    type = MAJOR;
                }

                // no need to check defaultValues (TRIVIAL change)

                if (type == TRIVIAL) {
                    int t1 = getOldDef().getRequiredType();
                    int t2 = getNewDef().getRequiredType();
                    if (t1 != t2) {
                        if (t2 == PropertyType.UNDEFINED) {
                            // changed getRequiredType to UNDEFINED (MINOR change)
                            type = MINOR;
                        } else {
                            // changed getRequiredType to specific type (MAJOR change)
                            type = MAJOR;
                        }
                    }
                    boolean b1 = getOldDef().isMultiple();
                    boolean b2 = getNewDef().isMultiple();
                    if (b1 != b2) {
                        if (b2) {
                            // changed multiple flag to true (MINOR change)
                            type = MINOR;
                        } else {
                            // changed multiple flag to false (MAJOR change)
                            type = MAJOR;
                        }
                    }
                }
            }
        }
    }

    public class ChildNodeDefDiff extends ChildItemDefDiff {

        ChildNodeDefDiff(QNodeDefinition oldDef, QNodeDefinition newDef) {
            super(oldDef, newDef);
        }

        public QNodeDefinition getOldDef() {
            return (QNodeDefinition) oldDef;
        }

        public QNodeDefinition getNewDef() {
            return (QNodeDefinition) newDef;
        }

        protected void init() {
            super.init();
            /**
             * only need to do comparison if base class implementation
             * detected a non-MAJOR modification (i.e. TRIVIAL or MINOR);
             * no need to check for additions or removals as this is already
             * handled in base class implementation.
             */
            if (isModified() && type != NONE && type != MAJOR) {

                boolean b1 = getOldDef().allowsSameNameSiblings();
                boolean b2 = getNewDef().allowsSameNameSiblings();
                if (b1 != b2 && !b2) {
                    // changed sameNameSiblings flag to false (MAJOR change)
                    type = MAJOR;
                }

                // no need to check defaultPrimaryType (TRIVIAL change)

                if (type == TRIVIAL) {
                    List<Name> l1 = Arrays.asList(getOldDef().getRequiredPrimaryTypes());
                    List<Name> l2 = Arrays.asList(getNewDef().getRequiredPrimaryTypes());
                    if (!l1.equals(l2)) {
                        if (l1.containsAll(l2)) {
                            // removed requiredPrimaryType (MINOR change)
                            type = MINOR;
                        } else {
                            // added requiredPrimaryType (MAJOR change)
                            type = MAJOR;
                        }
                    }
                }
            }
        }
    }
}