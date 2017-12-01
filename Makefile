compile:
	java StartRemotePeers;

rm:
	rm peer_1000/log_peer_1000.log || exit 0;
	rm peer_1001/log_peer_1001.log || exit 0;
	rm peer_1002/log_peer_1002.log || exit 0;
	rm peer_1003/log_peer_1003.log || exit 0;
	rm peer_1004/log_peer_1004.log || exit 0;
	rm peer_1001/test.dat || exit 0;
	rm peer_1002/test.dat || exit 0;
	rm peer_1003/test.dat || exit 0;
	rm peer_1004/test.dat || exit 0;
	
diff:
	diff peer_1000/test.dat peer_1001/test.dat || exit 0;
	diff peer_1000/test.dat peer_1002/test.dat || exit 0;
	diff peer_1000/test.dat peer_1003/test.dat || exit 0;
	diff peer_1000/test.dat peer_1004/test.dat || exit 0;