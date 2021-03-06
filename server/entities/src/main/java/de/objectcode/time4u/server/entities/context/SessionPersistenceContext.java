package de.objectcode.time4u.server.entities.context;

import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

import de.objectcode.time4u.server.entities.DayTagEntity;
import de.objectcode.time4u.server.entities.PersonEntity;
import de.objectcode.time4u.server.entities.ProjectEntity;
import de.objectcode.time4u.server.entities.TaskEntity;
import de.objectcode.time4u.server.entities.TeamEntity;
import de.objectcode.time4u.server.entities.TodoEntity;
import de.objectcode.time4u.server.entities.TodoGroupEntity;

public class SessionPersistenceContext implements IPersistenceContext
{
  private final Session m_session;

  public SessionPersistenceContext(final Session session)
  {
    m_session = session;
  }

  /**
   * {@inheritDoc}
   */
  public PersonEntity findPerson(final String personId, final long clientId)
  {
    PersonEntity person = (PersonEntity) m_session.get(PersonEntity.class, personId);

    if (person == null) {
      person = new PersonEntity(personId, -1L, clientId);
      person.setSurname(personId);

      m_session.persist(person);
    }

    return person;
  }

  /**
   * {@inheritDoc}
   */
  public ProjectEntity findProject(final String projectId, final long clientId)
  {
    ProjectEntity project = (ProjectEntity) m_session.get(ProjectEntity.class, projectId);

    if (project == null) {
      project = new ProjectEntity(projectId, -1L, clientId, projectId);

      m_session.persist(project);
    }

    return project;
  }

  /**
   * {@inheritDoc}
   */
  @SuppressWarnings("unchecked")
  public List<ProjectEntity> findAllChildrenDeep(final String parentKey)
  {
    final Criteria criteria = m_session.createCriteria(ProjectEntity.class);
    criteria.add(Restrictions.like("parentKey", parentKey + ":", MatchMode.START));
    criteria.addOrder(Order.asc("parentKey"));

    return criteria.list();
  }

  /**
   * {@inheritDoc}
   */
  public TaskEntity findTask(final String taskId, final long clientId)
  {
    TaskEntity task = (TaskEntity) m_session.get(TaskEntity.class, taskId);

    if (task == null) {
      task = new TaskEntity(taskId, -1L, clientId, null, taskId);

      m_session.persist(task);
    }

    return task;
  }

  /**
   * {@inheritDoc}
   */
  public TodoEntity findTodo(final String todoId, final long clientId)
  {
    TodoEntity todo = (TodoEntity) m_session.get(TodoEntity.class, todoId);

    if (todo == null) {
      todo = new TodoEntity(todoId, -1L, clientId, todoId);

      m_session.persist(todo);
    }

    return todo;
  }

  public TodoGroupEntity findTodoGroup(final String todoGroupId, final long clientId)
  {
    TodoGroupEntity todoGroup = (TodoGroupEntity) m_session.get(TodoGroupEntity.class, todoGroupId);

    if (todoGroup == null) {
      todoGroup = new TodoGroupEntity(todoGroupId, -1L, clientId, todoGroupId);

      m_session.persist(todoGroup);
    }

    return todoGroup;
  }

  public TeamEntity findTeam(final String teamId, final long clientId)
  {
    TeamEntity team = (TeamEntity) m_session.get(TeamEntity.class, teamId);

    if (team == null) {
      team = new TeamEntity(teamId, -1L, clientId, teamId);

      m_session.persist(team);
    }

    return team;
  }

  public DayTagEntity findDayTag(final String name)
  {
    return (DayTagEntity) m_session.get(DayTagEntity.class, name);
  }

  public void delete(final Object entity)
  {
    m_session.delete(entity);
  }

  public void persist(final Object entity)
  {
    m_session.persist(entity);
  }

}
