default_target: rpm
deb: zookeeper.deb
rpm: zookeeper-3.6.4-1.x86_64.rpm

zookeeper-3.6.4-1.x86_64.rpm: zookeeper.deb rpm_pre_install rmp_post_install
	rm -rf zookeeper-3.6.4
	alien --to-rpm  -v --generate  zookeeper.deb
	sed -i '9iAutoReqProv: no' zookeeper-3.6.4/zookeeper-3.6.4-1.spec
	cd zookeeper-3.6.4 && rpmbuild --buildroot=/home/atkins/workingspace/hadoop-autodeployment/zookeeper-deb/zookeeper-3.6.4/ -bb --target=x86_64 zookeeper-3.6.4-1.spec
zookeeper.deb:
	cd src && find . -type f ! -regex '.*.hg.*' ! -regex '.*?debian-binary.*' ! -regex '.*?DEBIAN.*' -printf '%P ' | xargs md5sum > DEBIAN/md5sums
	cd src && find etc/zookeeper/ -type f -printf '/%p\n' > DEBIAN/conffiles
	dpkg-deb --build  src/ zookeeper.deb

rpm_pre_install: 
	cp src/DEBIAN/preinst ./rpm_pre_install.sh

rmp_post_install:
	cp src/DEBIAN/postinst ./rpm_post_install.sh
