/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.hibernate.collection.internal;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.loader.CollectionAliases;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.type.Type;


/**
 * A persistent wrapper for a <tt>java.util.Set</tt>. The underlying
 * collection is a <tt>HashSet</tt>.
 *
 * @see java.util.HashSet
 * @author Gavin King
 */
@SuppressWarnings({"serial","rawtypes"})
public class PersistentSet extends AbstractPersistentCollection implements java.util.Set {
  protected Set set;
	protected transient List tempList;

	/**
	 * Empty constructor.
	 * <p/>
	 * Note: this form is not ever ever ever used by Hibernate; it is, however,
	 * needed for SOAP libraries and other such marshalling code.
	 */
	public PersistentSet() {
		// intentionally empty
	}

	/**
	 * Constructor matching super.  Instantiates a lazy set (the underlying
	 * set is un-initialized).
	 *
	 * @param session The session to which this set will belong.
	 */
	public PersistentSet(SessionImplementor session) {
		super( session );
	}

	/**
	 * Instantiates a non-lazy set (the underlying set is constructed
	 * from the incoming set reference).
	 *
	 * @param session The session to which this set will belong.
	 * @param set The underlying set data.
	 */
	public PersistentSet(SessionImplementor session, java.util.Set set) {
		super( session );
		// Sets can be just a view of a part of another collection.
		// do we need to copy it to be sure it won't be changing
		// underneath us?
		// ie. this.set.addAll(set);
		this.set = set;
		setInitialized();
		setDirectlyAccessible( true );
	}

	@Override
	@SuppressWarnings( {"unchecked"})
	public Serializable getSnapshot(CollectionPersister persister) throws HibernateException {
		final HashMap clonedSet = new HashMap( set.size() );
		for ( Object aSet : set ) {
			final Object copied = persister.getElementType().deepCopy( aSet, persister.getFactory() );
			clonedSet.put( copied, copied );
		}
		return clonedSet;
	}

	@Override
	public Collection getOrphans(Serializable snapshot, String entityName) throws HibernateException {
		final java.util.Map sn = (java.util.Map) snapshot;
		return getOrphans( sn.keySet(), set, entityName, getSession() );
	}

	@Override
	public boolean equalsSnapshot(CollectionPersister persister) throws HibernateException {
		final Type elementType = persister.getElementType();
		final java.util.Map sn = (java.util.Map) getSnapshot();
		if ( sn.size()!=set.size() ) {
			return false;
		}
		else {
			for ( Object test : set ) {
				final Object oldValue = sn.get( test );
				if ( oldValue == null || elementType.isDirty( oldValue, test, getSession() ) ) {
					return false;
				}
			}
			return true;
		}
	}

	@Override
	public boolean isSnapshotEmpty(Serializable snapshot) {
		return ( (java.util.Map) snapshot ).isEmpty();
	}

	@Override
	public void beforeInitialize(CollectionPersister persister, int anticipatedSize) {
		this.set = (Set) persister.getCollectionType().instantiate( anticipatedSize );
	}

	@Override
	@SuppressWarnings("unchecked")
	public void initializeFromCache(CollectionPersister persister, Serializable disassembled, Object owner)
			throws HibernateException {
		final Serializable[] array = (Serializable[]) disassembled;
		final int size = array.length;
		beforeInitialize( persister, size );
		for ( Serializable arrayElement : array ) {
			final Object assembledArrayElement = persister.getElementType().assemble( arrayElement, getSession(), owner );
			if ( assembledArrayElement != null ) {
				set.add( assembledArrayElement );
			}
		}
	}

	@Override
	public boolean isCollectionEmpty() {
		return set.isEmpty();
	}

	@Override
	public int size() {
		return readSize() ? getCachedSize() : set.size();
	}

	@Override
	public boolean isEmpty() {
		return readSize() ? getCachedSize()==0 : set.isEmpty();
	}

	@Override
	public boolean contains(Object object) {
		final Boolean exists = readElementExistence( object );
		return exists == null
				? set.contains( object )
				: exists;
	}

	@Override
	public Iterator iterator() {
		read();
		return new IteratorProxy( set.iterator() );
	}

	@Override
	public Object[] toArray() {
		read();
		return set.toArray();
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object[] toArray(Object[] array) {
		read();
		return set.toArray( array );
	}

	@Override
	 @SuppressWarnings("unchecked")
	public boolean add(Object value) {
		final Boolean exists = isOperationQueueEnabled() ? readElementExistence( value ) : null;
		if ( exists == null ) {
			initialize( true );
			if ( set.add( value ) ) {
				dirty();
				return true;
			}
			else {
				return false;
			}
		}
		else if ( exists ) {
			return false;
		}
		else {
			queueOperation( new SimpleAdd( value ) );
			return true;
		}
	}

	@Override
	public boolean remove(Object value) {
		final Boolean exists = isPutQueueEnabled() ? readElementExistence( value ) : null;
		if ( exists == null ) {
			initialize( true );
			if ( set.remove( value ) ) {
				dirty();
				return true;
			}
			else {
				return false;
			}
		}
		else if ( exists ) {
			queueOperation( new SimpleRemove( value ) );
			return true;
		}
		else {
			return false;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean containsAll(Collection coll) {
		read();
		return set.containsAll( coll );
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean addAll(Collection coll) {
		if ( coll.size() > 0 ) {
			initialize( true );
			if ( set.addAll( coll ) ) {
				dirty();
				return true;
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean retainAll(Collection coll) {
		initialize( true );
		if ( set.retainAll( coll ) ) {
			dirty();
			return true;
		}
		else {
			return false;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean removeAll(Collection coll) {
		if ( coll.size() > 0 ) {
			initialize( true );
			if ( set.removeAll( coll ) ) {
				dirty();
				return true;
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
	}

	@Override
	public void clear() {
		if ( isClearQueueEnabled() ) {
			queueOperation( new Clear() );
		}
		else {
			initialize( true );
			if ( !set.isEmpty() ) {
				set.clear();
				dirty();
			}
		}
	}

	@Override
	public String toString() {
		read();
		return set.toString();
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object readFrom(
			ResultSet rs,
			CollectionPersister persister,
			CollectionAliases descriptor,
			Object owner) throws HibernateException, SQLException {
		final Object element = persister.readElement( rs, owner, descriptor.getSuffixedElementAliases(), getSession() );
		if ( element != null ) {
			tempList.add( element );
		}
		return element;
	}

	@Override
	public void beginRead() {
		super.beginRead();
		tempList = new ArrayList();
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean endRead() {
		set.addAll( tempList );
		tempList = null;
		setInitialized();
		return true;
	}

	@Override
	public Iterator entries(CollectionPersister persister) {
		return set.iterator();
	}

	@Override
	public Serializable disassemble(CollectionPersister persister) throws HibernateException {
		final Serializable[] result = new Serializable[ set.size() ];
		final Iterator itr = set.iterator();
		int i=0;
		while ( itr.hasNext() ) {
			result[i++] = persister.getElementType().disassemble( itr.next(), getSession(), null );
		}
		return result;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Iterator getDeletes(CollectionPersister persister, boolean indexIsFormula) throws HibernateException {
		final Type elementType = persister.getElementType();
		final java.util.Map sn = (java.util.Map) getSnapshot();
		final ArrayList deletes = new ArrayList( sn.size() );

		Iterator itr = sn.keySet().iterator();
		while ( itr.hasNext() ) {
			final Object test = itr.next();
			if ( !set.contains( test ) ) {
				// the element has been removed from the set
				deletes.add( test );
			}
		}

		itr = set.iterator();
		while ( itr.hasNext() ) {
			final Object test = itr.next();
			final Object oldValue = sn.get( test );
			if ( oldValue!=null && elementType.isDirty( test, oldValue, getSession() ) ) {
				// the element has changed
				deletes.add( oldValue );
			}
		}

		return deletes.iterator();
	}

	@Override
	public boolean needsInserting(Object entry, int i, Type elemType) throws HibernateException {
		final Object oldValue = ( (java.util.Map) getSnapshot() ).get( entry );
		// note that it might be better to iterate the snapshot but this is safe,
		// assuming the user implements equals() properly, as required by the Set
		// contract!
		return oldValue == null || elemType.isDirty( oldValue, entry, getSession() );
	}

	@Override
	public boolean needsUpdating(Object entry, int i, Type elemType) {
		return false;
	}

	@Override
	public boolean isRowUpdatePossible() {
		return false;
	}

	@Override
	public Object getIndex(Object entry, int i, CollectionPersister persister) {
		throw new UnsupportedOperationException("Sets don't have indexes");
	}

	@Override
	public Object getElement(Object entry) {
		return entry;
	}

	@Override
	public Object getSnapshotElement(Object entry, int i) {
		throw new UnsupportedOperationException("Sets don't support updating by element");
	}

	@Override
	public boolean equals(Object other) {
		read();
		return set.equals( other );
	}

	@Override
	public int hashCode() {
		read();
		return set.hashCode();
	}

	@Override
	public boolean entryExists(Object key, int i) {
		return true;
	}

	@Override
	public boolean isWrapper(Object collection) {
		return set==collection;
	}

	final class Clear implements DelayedOperation {
		@Override
		public void operate() {
			set.clear();
		}

		@Override
		public Object getAddedInstance() {
			return null;
		}

		@Override
		public Object getOrphan() {
			throw new UnsupportedOperationException("queued clear cannot be used with orphan delete");
		}
	}

	final class SimpleAdd implements DelayedOperation {
		private Object value;
		
		public SimpleAdd(Object value) {
			this.value = value;
		}

		@Override
		@SuppressWarnings("unchecked")
		public void operate() {
			set.add( value );
		}

		@Override
		public Object getAddedInstance() {
			return value;
		}

		@Override
		public Object getOrphan() {
			return null;
		}
	}

	final class SimpleRemove implements DelayedOperation {
		private Object value;
		
		public SimpleRemove(Object value) {
			this.value = value;
		}

		@Override
		public void operate() {
			set.remove( value );
		}

		@Override
		public Object getAddedInstance() {
			return null;
		}

		@Override
		public Object getOrphan() {
			return value;
		}
	}
}
