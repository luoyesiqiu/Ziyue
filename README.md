#子曰
> 子曰是Android平台上一款匿名聊天软件,打开注册后即从服务器随机匹配一位陌生人来进行聊天，支持显示emoji,支持发送命令来对对方手机进行操作。客户端使用Smack库，服务器使用openfire。 
## 项目难点
在服务器端匹配陌生人的功能的实现。 
## 项目难点解决
使用Redis数据库模拟队列，当用户打开软件和退出时来进行 相应的入队和出队操作。 
## Openfire服务端插件源码
https://github.com/luoyesiqiu/MatchUserPlugin

