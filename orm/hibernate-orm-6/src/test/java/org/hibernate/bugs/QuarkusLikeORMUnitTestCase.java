package org.hibernate.bugs;

import org.hibernate.bugs.QuarkusLikeORMUnitTestCase.EntityWithCreationTimestamp;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.annotations.CreationTimestamp;

import org.hibernate.testing.bytecode.enhancement.CustomEnhancementContext;
import org.hibernate.testing.bytecode.enhancement.extension.BytecodeEnhanced;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DomainModel(
		annotatedClasses = {
				EntityWithCreationTimestamp.class
		}
)
@ServiceRegistry(
		settings = {
				@Setting(name = AvailableSettings.SHOW_SQL, value = "true"),
				@Setting(name = AvailableSettings.FORMAT_SQL, value = "true"),
				@Setting(name = AvailableSettings.PREFERRED_POOLED_OPTIMIZER, value = "pooled-lo"),
				@Setting(name = AvailableSettings.DEFAULT_BATCH_FETCH_SIZE, value = "16"),
				@Setting(name = AvailableSettings.QUERY_PLAN_CACHE_MAX_SIZE, value = "2048"),
				@Setting(name = AvailableSettings.DEFAULT_NULL_ORDERING, value = "none"),
				@Setting(name = AvailableSettings.IN_CLAUSE_PARAMETER_PADDING, value = "true"),
				@Setting(name = AvailableSettings.SEQUENCE_INCREMENT_SIZE_MISMATCH_STRATEGY, value = "none"),
				@Setting(name = AvailableSettings.ORDER_UPDATES, value = "true"),
		}
)
@SessionFactory
@BytecodeEnhanced
@CustomEnhancementContext(QuarkusLikeEnhancementContext.class)
class QuarkusLikeORMUnitTestCase {

	@DisplayName("Given an entity with a @CreationTimestamp-annotated property," +
			"When the property is manually changed after persistence," +
			"Then the property is updated")
	@Test
	void creationTimestampModificationTest(SessionFactoryScope scope) throws Exception {
		AtomicReference<Long> testEntityId = new AtomicReference<>();
		Instant manualCreationTime = Instant.parse("2020-01-10T10:00:00Z");

		// First transaction - create and modify
		scope.inTransaction(session -> {
			EntityWithCreationTimestamp myEntity = new EntityWithCreationTimestamp();
			session.persist(myEntity); // Make sure createdOn is initially automatically set
			session.flush();
			testEntityId.set(myEntity.getId());

			myEntity.setUnrelatedPropertyEdited(true);
			myEntity.setCreatedOn(manualCreationTime); // Overwriting @CreationTimestamp annotated field
		});

		// Second transaction - reload
		scope.inTransaction(session -> {
			EntityWithCreationTimestamp reloadedEntity = session.find(EntityWithCreationTimestamp.class, testEntityId.get());
			assertTrue(reloadedEntity.isUnrelatedPropertyEdited());
			assertEquals(manualCreationTime, reloadedEntity.getCreatedOn());
		});
	}

	@DisplayName("Given an entity with a @CreationTimestamp-annotated property," +
			"When the property is manually changed after persistence within a separate transaction," +
			"Then the property is updated")
	@Test
	void creationTimestampModificationTestWithMultipleTransactions(SessionFactoryScope scope) throws Exception {
		AtomicReference<Long> testEntityId = new AtomicReference<>();
		Instant manualCreationTime = Instant.parse("2020-01-10T10:00:00Z");

		// First transaction - create
		scope.inTransaction(session -> {
			EntityWithCreationTimestamp myEntity = new EntityWithCreationTimestamp();
			session.persist(myEntity); // Make sure createdOn is initially automatically set
			session.flush();
			testEntityId.set(myEntity.getId());
		});

		// Second transaction - Change createdOn in separate transaction
		scope.inTransaction(session -> {
			EntityWithCreationTimestamp reloadedEntity = session.find(EntityWithCreationTimestamp.class, testEntityId.get());
			reloadedEntity.setUnrelatedPropertyEdited(true);
			reloadedEntity.setCreatedOn(manualCreationTime); // Overwriting @CreationTimestamp annotated field
		});

		// Third transaction - reload and confirm createdOn change
		scope.inTransaction(session -> {
			EntityWithCreationTimestamp reloadedEntity2 = session.find(EntityWithCreationTimestamp.class, testEntityId.get());
			assertTrue(reloadedEntity2.isUnrelatedPropertyEdited());
			assertEquals(manualCreationTime, reloadedEntity2.getCreatedOn());
		});
	}

	@Entity
	public static class EntityWithCreationTimestamp {
		@CreationTimestamp
		private Instant createdOn;

		private boolean unrelatedPropertyEdited = false;

		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		private Long id;

		public EntityWithCreationTimestamp() {
		}

		public Instant getCreatedOn() {
			return createdOn;
		}

		public void setCreatedOn(Instant createdOn) {
			this.createdOn = createdOn;
		}

		public boolean isUnrelatedPropertyEdited() {
			return unrelatedPropertyEdited;
		}

		public void setUnrelatedPropertyEdited(boolean unrelatedPropertyEdited) {
			this.unrelatedPropertyEdited = unrelatedPropertyEdited;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public Long getId() {
			return id;
		}
	}
}