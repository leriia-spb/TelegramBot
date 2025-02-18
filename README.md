# ReminderBot - Telegram Бот для управления напоминаниями 

**ReminderBot** — это Telegram-бот для создания и управления вашими напоминаниями. Легко планируйте свои дела, управляйте задачами и никогда не пропускайте важные события. Поддерживаются как одноразовые напоминания, так и регулярные задачи с использованием Cron-выражений.

---

## Особенности

- **Одноразовые напоминания:**  
  Укажите дату и время, и бот напомнит вам о событии.
  
- **Периодические напоминания:**  
  Настраивайте напоминания с помощью Cron-выражений для ежедневных, еженедельных или ежемесячных напоминаний.

- **Удобное управление:**  
  Просматривайте список всех ваших напоминаний, удаляйте ненужные или устаревшие задачи.

- **Валидация данных:**  
  Умный ввод команд с проверкой формата времени и Cron-выражений.

---

## Установка

1. **Клонируйте репозиторий:**
   ```bash
   git clone https://github.com/username/reminderbot.git
   cd reminderbot
   # Настройка окружения

2. Убедитесь, что у вас установлены Scala и sbt.
3. Также установите базу данных SQLite.

## Добавьте токен Telegram

В файле `application.conf` укажите токен вашего бота:

```hocon
bot {
  token = "ВАШ_ТОКЕН"
}
```

## Запустите бота 

Для запуска используйте команду:

sbt run



