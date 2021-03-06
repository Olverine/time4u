package de.objectcode.time4u.server.ejb.seam.impl;

import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.AutoCreate;
import org.jboss.seam.annotations.Factory;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.datamodel.DataModel;
import org.jboss.seam.annotations.security.Restrict;

import de.objectcode.time4u.server.api.data.EntityType;
import de.objectcode.time4u.server.ejb.seam.api.ITeamServiceLocal;
import de.objectcode.time4u.server.entities.PersonEntity;
import de.objectcode.time4u.server.entities.ProjectEntity;
import de.objectcode.time4u.server.entities.TeamEntity;
import de.objectcode.time4u.server.entities.account.UserAccountEntity;
import de.objectcode.time4u.server.entities.revision.ILocalIdGenerator;
import de.objectcode.time4u.server.entities.revision.IRevisionGenerator;
import de.objectcode.time4u.server.entities.revision.IRevisionLock;

@Stateless
@Local(ITeamServiceLocal.class)
@org.jboss.annotation.ejb.LocalBinding(jndiBinding = "time4u-server/seam/TeamServiceSeam/local")
@org.jboss.ejb3.annotation.LocalBinding(jndiBinding = "time4u-server/seam/TeamServiceSeam/local")
@Name("TeamService")
@AutoCreate
@Scope(ScopeType.CONVERSATION)
public class TeamServiceSeam implements ITeamServiceLocal
{
  @PersistenceContext(unitName = "time4u")
  private EntityManager m_manager;

  @EJB
  private IRevisionGenerator m_revisionGenerator;

  @EJB
  private ILocalIdGenerator m_idGenerator;

  @DataModel("admin.teamList")
  List<TeamEntity> m_teams;

  @SuppressWarnings("unchecked")
  @Factory("admin.teamList")
  @Restrict("#{s:hasRole('admin')}")
  public void initTeams()
  {
    final Query query = m_manager.createQuery("from " + TeamEntity.class.getName() + " t where t.deleted = false");

    m_teams = query.getResultList();
  }

  public TeamEntity getTeam(final String id)
  {
    return m_manager.find(TeamEntity.class, id);
  }

  @Restrict("#{s:hasRole('admin')}")
  public void storeTeam(final TeamEntity teamEntity)
  {
    final IRevisionLock revisionLock = m_revisionGenerator.getNextRevision(EntityType.TEAM, null);
    final IRevisionLock personRevisionLock = m_revisionGenerator.getNextRevision(EntityType.PERSON, null);

    if (teamEntity.getId() == null) {
      teamEntity.setId(m_idGenerator.generateLocalId(EntityType.TEAM));
    }
    teamEntity.setRevision(revisionLock.getLatestRevision());
    teamEntity.setLastModifiedByClient(m_idGenerator.getClientId());
    m_manager.merge(teamEntity);

    for (final PersonEntity person : teamEntity.getMembers()) {
      person.setLastModifiedByClient(m_idGenerator.getClientId());
      person.setRevision(personRevisionLock.getLatestRevision());
    }
    initTeams();
  }

  @SuppressWarnings("unchecked")
  @Restrict("#{s:hasRole('user')}")
  public List<TeamEntity> getTeamsByPersonId(final String identity){
    
    final UserAccountEntity userAccount = m_manager.find(UserAccountEntity.class, identity);
    final StringBuffer queryString = new StringBuffer(" select t" + 
      " from " + TeamEntity.class.getName()
      + " t left outer join t.owners to"
        + " where to.id in ( :personId )");
    
    final Query query = m_manager.createQuery(queryString.toString());
    
    query.setParameter("personId", userAccount.getPerson().getId());
    
    return query.getResultList();
  }
}
