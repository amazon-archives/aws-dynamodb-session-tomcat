package com.amazonaws.services.dynamodb.sessionmanager;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.catalina.Manager;
import org.apache.catalina.session.StandardSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.amazonaws.services.dynamodb.sessionmanager.converters.SessionConversionException;
import com.amazonaws.services.dynamodb.sessionmanager.converters.TestSessionFactory;

public class DynamoDBSessionStoreTest {

    @Mock
    private DynamoSessionStorage storage;

    private StandardSession session;

    private Manager manager;

    private DynamoDBSessionStore store;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        TestSessionFactory factory = new TestSessionFactory();
        manager = factory.getManager();
        session = factory.createStandardSession();
    }

    @Test
    public void whenDeleteCorruptSessionsIsTrue_CorruptSessionsAreDeleted() throws Exception {
        buildSessionStore(true);
        saveAndLoadCorruptSession();
        assertSessionIsDeleted();
    }

    @Test
    public void whenDeleteCorruptSessionsIsTrue_ValidSessionsAreNotDeleted() throws Exception {
        buildSessionStore(true);
        saveAndLoadValidSession();
        assertSessionIsNotDeleted();
    }

    @Test
    public void whenDeleteCorruptSessionsIsFalse_ValidSessionsAreNotDeleted() throws Exception {
        buildSessionStore(false);
        saveAndLoadValidSession();
        assertSessionIsNotDeleted();
    }

    @Test
    public void whenDeleteCorruptSessionsIsFalse_CorruptSessionsAreNotDeleted() throws Exception {
        buildSessionStore(false);
        saveAndLoadCorruptSession();
        assertSessionIsNotDeleted();
    }

    private void assertSessionIsDeleted() {
        verify(storage).deleteSession(session.getId());
    }

    private void assertSessionIsNotDeleted() {
        verify(storage, never()).deleteSession(session.getId());
    }

    /**
     * Simulates the successful saving of a session and failure to load a corrupted session
     */
    private void saveAndLoadCorruptSession() throws Exception {
        store.save(session);
        when(storage.loadSession(session.getId())).thenThrow(new SessionConversionException(""));
        store.load(session.getId());
    }

    /**
     * Simulates the successful saving and loading of a session
     */
    private void saveAndLoadValidSession() throws Exception {
        store.save(session);
        when(storage.loadSession(session.getId())).thenReturn(session);
        store.load(session.getId());
    }

    private void buildSessionStore(boolean deleteCorruptSessions) {
        this.store = new DynamoDBSessionStore(storage, deleteCorruptSessions);
        this.store.setManager(manager);
    }
}
