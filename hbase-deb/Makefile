default_target: rpm
all: rpm
deb: hbase.deb
rpm: hbase-0.98.1-1.x86_64.rpm rpm_pre_install rmp_post_install

hbase.deb: md5sums conffiles
	dpkg-deb --build  src/ hbase.deb

md5sums:
	cd src && find . -type f ! -regex '.*.hg.*' ! -regex '.*?debian-binary.*' ! -regex '.*?DEBIAN.*' -printf '%P ' | xargs md5sum > DEBIAN/md5sums

conffiles:
	cd src && find etc/hbase/ -type f -printf '/%p\n' > DEBIAN/conffiles

hbase-0.98.1-1.x86_64.rpm: hbase-0.98.1-1.spec
	cd hbase-0.98.1 && rpmbuild --buildroot=/home/atkins/workingspace/hadoop-autodeployment/hbase-deb/hbase-0.98.1/ -bb --target=x86_64 hbase-0.98.1-1.spec

hbase-0.98.1-1.spec: hbase-0.98.1 
        sed -i '9iAutoReqProv: no' hbase-0.98.1/hbase-0.98.1-1.spec

hbase-0.98.1: hbase.deb
	rm -rf hbase-0.98.1
	alien --to-rpm  -v --generate  hbase.deb

rpm_pre_install:
	cp src/DEBIAN/preinst ./rpm_pre_install.sh

rmp_post_install:
	cp src/DEBIAN/postinst ./rpm_post_install.sh

clean:
	rm hbase.deb
	 
