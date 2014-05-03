default_target: rpm
rpm: hbase-0.98.1-1.x86_64.rpm
deb: hbase.deb

hbase-0.98.1-1.x86_64.rpm: hbase.deb rpm_pre_install rmp_post_install
	rm -rf hbase-0.98.1
	alien --to-rpm  -v --generate  hbase.deb
	sed -i '9iAutoReqProv: no' hbase-0.98.1/hbase-0.98.1-1.spec
	cd hbase-0.98.1 && rpmbuild --buildroot=/home/atkins/workingspace/hadoop-autodeployment/hbase-deb/hbase-0.98.1/ -bb --target=x86_64 hbase-0.98.1-1.spec
hbase.deb:
	cd src && find . -type f ! -regex '.*.hg.*' ! -regex '.*?debian-binary.*' ! -regex '.*?DEBIAN.*' -printf '%P ' | xargs md5sum > DEBIAN/md5sums
	cd src && find etc/hbase/ -type f -printf '/%p\n' > DEBIAN/conffiles
	dpkg-deb --build  src/ hbase.deb

rpm_pre_install: 
	cp src/DEBIAN/preinst ./rpm_pre_install.sh

rmp_post_install:
	cp src/DEBIAN/postinst ./rpm_post_install.sh
	 
