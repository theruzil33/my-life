import { ACTIVITY_TYPE } from './habitOptions'
import ActivityListTab from './ActivityListTab'

export default function TasksTab({ refreshToken = 0 }) {
  return (
    <ActivityListTab
      type={ACTIVITY_TYPE.TASK}
      idPrefix="tasks"
      addLabel="Добавить задачу"
      emptyMessage="На выбранную дату задач нет."
      modalTitle="Новая задача"
      entityLabel="задачи"
      renameTitle="Переименовать задачу"
      refreshToken={refreshToken}
    />
  )
}
